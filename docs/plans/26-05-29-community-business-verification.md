# 커뮤니티 사업자 인증 + Discord 알림 추상화

> 구현 완료 (브랜치 `feat/community-business-verification`, 2026-05-29). 구현 계획·진행 내역: `docs/superpowers/plans/2026-05-29-community-business-verification.md`

- 작성일: 2026-05-29
- 브랜치: `feat/community-business-verification`
- 관련 문서: [설계 SSOT](../DESIGN.md) · [멀티테넌시 격리](../conventions/26-05-25-multitenancy-isolation.md) · [에러 코드](../ERROR_CODES.md)

---

## 1. 배경 & 목표

커뮤니티는 **사업자(꽃집 운영자)만** 이용 가능해야 한다. 사업자등록증을 제출해 인증된 사용자에게만 커뮤니티 탭(읽기·쓰기 전부)을 연다.

1차 버전은 **수동 검토**다. 사용자가 등록증 이미지 + 핵심 정보를 제출하면 Discord로 알림이 오고, 관리자(=운영자 본인)가 이미지를 눈으로 확인한 뒤 DB에서 직접 승인/거절한다. 향후 OCR + 국세청 진위확인 API로 자동화할 수 있도록 데이터·구조를 미리 준비한다.

함께, 앞으로 늘어날 Discord 운영 알림(가입 등)을 위해 **재사용 가능한 Discord 알림 모듈**을 도입하고, 그 첫 적용처로 **사업자 인증 신청 알림**과 **유저 가입 알림** 두 가지를 연동한다.

### 범위 (이번 PR)

- ✅ 사업자 인증 신청/조회 API + 수동 검토용 데이터 모델
- ✅ 커뮤니티 게이팅(`@RequiresBusinessVerified`)
- ✅ 재사용 Discord 알림 모듈(`common/notification/discord/`)
- ✅ 사업자 인증 신청 알림 (verification 도메인 이벤트)
- ✅ 유저 가입 알림 (user/auth 도메인 이벤트)

### 범위 밖 (향후)

- ❌ 관리자 검토 API (지금은 수동 DB 업데이트) — 승인/거절은 운영자가 직접 SQL로 처리
- ❌ OCR / 국세청 진위확인 API 자동화
- ❌ 기존 `DiscordErrorReporter`의 신규 모듈로의 통합 (잘 동작 중 + 에러 전용 로직 보유 → 별도 작업)

---

## 2. 데이터 모델 — `business_verifications`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | bigserial PK | |
| `user_id` | bigint NOT NULL, indexed | 멀티테넌시 격리 키 (간접참조, FK 없음 — repo 컨벤션) |
| `business_number` | varchar(10) NOT NULL | 사업자번호 10자리(하이픈 제거 정규화 저장) |
| `business_name` | varchar(255) NOT NULL | 상호 |
| `representative_name` | varchar(100) NOT NULL | 대표자명 |
| `business_license_url` | text NOT NULL | 사업자등록증 이미지 URL (S3/CloudFront) |
| `status` | varchar(20) NOT NULL | `PENDING` / `APPROVED` / `REJECTED` |
| `reject_reason` | text NULL | 거절 사유 |
| `reviewed_at` | timestamptz NULL | 검토(승인/거절) 시각 |
| `created_at` / `updated_at` | timestamptz NOT NULL | `BaseEntity` 자동 관리 |

### 규칙

- **인증됨 판정 = `EXISTS(business_verifications WHERE user_id = ? AND status = 'APPROVED')`**. `users`에 별도 플래그를 두지 않는다(정규화 유지 — `SubscriptionService.requireActiveSubscription`와 동일 철학).
- **중복 방지**: user당 `PENDING` 1건만 허용. 서비스에서 검사 + 부분 유니크 인덱스(`WHERE status = 'PENDING'`).
- **재신청**: 새 행을 추가한다. 유효 상태는 최신 행 기준.
- **수동 승인/거절** (이번 단계): 운영자가 직접 SQL로 처리.
  - 승인: `UPDATE business_verifications SET status='APPROVED', reviewed_at=now() WHERE id=:id;`
  - 거절: `UPDATE business_verifications SET status='REJECTED', reject_reason=:reason, reviewed_at=now() WHERE id=:id;`

### 상태 enum

`BusinessVerificationStatuses`(도메인 enum) — `PENDING`, `APPROVED`, `REJECTED`.

---

## 3. API (사용자용만 — 관리자 API 없음)

도메인: `kr.ai.flori.verification`, 베이스 경로 `/verification/business`. 모든 엔드포인트 JWT 인증, 신원은 `TenantContext`에서만 도출.

> 이 엔드포인트들은 **게이팅 대상이 아니다**(인증을 하는 입구이므로 닭-달걀 방지).

### 3.1 `POST /verification/business/upload-target`

등록증 이미지 업로드용 presigned PUT URL 발급.

- 키 규칙: `business-licenses/{userId}/{uuid}.{ext}` — userId를 키에 박아 소유권을 표현(사진 도메인과 동일 패턴).
- `S3PresignService.ALLOWED_KEY_PREFIXES`에 `business-licenses/` 추가.
- 요청: `{ contentType }` (이미지 MIME 화이트리스트 검증)
- 응답: `{ uploadUrl, fileUrl, expiresInSeconds }` (`PresignedUpload` 재사용)

### 3.2 `POST /verification/business`

신청 제출.

- 요청: `{ businessNumber, businessName, representativeName, businessLicenseUrl }`
  - `businessNumber`: `@Pattern("\\d{10}")` (하이픈 제거 후 10자리)
  - `businessName`, `representativeName`: `@NotBlank` + 길이 제한
  - `businessLicenseUrl`: `@NotBlank`, **키가 `business-licenses/{현재 userId}/`로 시작하는지 소유권 검증**
- 동작: 기존 `PENDING`/`APPROVED` 존재 시 **409**(`E-VERIFICATION-xxx`). 아니면 `PENDING` 행 생성.
- 커밋 후 **`BusinessVerificationSubmittedEvent` 발행** → 비동기 Discord 알림.
- 응답: 현재 상태(`status`, `submittedAt`).

### 3.3 `GET /verification/business/me`

내 인증 상태 조회 (모바일이 "사업자 인증" 화면 vs 잠금해제 판단).

- 응답: `{ status, rejectReason?, submittedAt?, reviewedAt? }` (최신 행 기준).
  - 인증 이력이 전혀 없으면 **200 + `{ status: "NONE" }`** 으로 응답한다(404 대신). 모바일이 단일 분기(`NONE` → 인증 화면, `PENDING` → 대기, `REJECTED` → 사유+재신청, `APPROVED` → 잠금해제)로 처리하도록 한다. `status`는 응답 전용으로 `NONE`을 포함하며, DB enum에는 저장되지 않는다.

---

## 4. 게이팅 — `@RequiresBusinessVerified`

`subscriptions/gating`의 `@RequiresSubscription` + `SubscriptionAccessInterceptor`를 그대로 미러링한다.

```
verification/gating/
├── RequiresBusinessVerified.kt       # @Target(FUNCTION, CLASS) 애너테이션
├── BusinessVerifiedInterceptor.kt    # 핸들러 진입 전 APPROVED EXISTS 검사 → 없으면 403
└── BusinessVerificationWebConfig.kt  # 인터셉터 등록
```

- `BusinessVerifiedInterceptor`: 애너테이션 붙은 핸들러 진입 시, 현재 사용자(`TenantContext`)에 `APPROVED` 행이 있는지 검사. 없으면 **403**(`E-VERIFICATION-xxx`, 미인증). 서비스는 `ObjectProvider`로 지연 주입(슬라이스 테스트 호환).
- **`CommunityController` 클래스에 `@RequiresBusinessVerified` 부착** → 커뮤니티 읽기·쓰기 전 엔드포인트 잠금.

---

## 5. Discord 알림 추상화 — `common/notification/discord/`

### 설계 원칙

- **common = "어떻게 보내나"(전송 원시 도구), 도메인 = "무엇을/언제 보내나"** 로 책임 분리.
- transport는 **RestClient**(이미 `DiscordErrorReporter`에서 사용). Feign/Spring Cloud는 도입하지 않는다 — 단순 웹훅 POST에 과한 의존성.
- **이벤트 기반 비동기**: `@Async @TransactionalEventListener(AFTER_COMMIT)`. DB 커밋 성공 시에만 발송, 응답 스레드 비차단, best-effort(실패해도 본 작업 성공).

### 구성

```
common/notification/discord/
├── DiscordProperties.kt    # @ConfigurationProperties("discord")
├── DiscordChannel.kt       # enum: SIGNUP, VERIFICATION (논리 채널 → URL 매핑)
├── DiscordMessage.kt       # 페이로드 (content 또는 embeds)
└── DiscordNotifier.kt      # notify(channel, message) — @Async best-effort RestClient POST
```

```kotlin
enum class DiscordChannel(val url: (DiscordProperties) -> String) {
    SIGNUP({ it.signupWebhookUrl }),
    VERIFICATION({ it.verificationWebhookUrl }),
}
```

- `DiscordNotifier.notify(channel, message)`: 채널→URL 해석, URL 공백이면 콘솔 로깅 폴백(로컬 부팅 가능), `runCatching`으로 전송 실패 무시(로깅만).

### 환경변수 / `application.yml`

```yaml
discord:
  webhook-url: ${DISCORD_WEBHOOK_URL:}                       # 기존 — 운영 에러(DiscordErrorReporter)
  signup-webhook-url: ${DISCORD_SIGNUP_WEBHOOK_URL:}         # 신규 — 유저 가입
  verification-webhook-url: ${DISCORD_VERIFICATION_WEBHOOK_URL:}  # 신규 — 사업자 인증 신청
```

> 실제 웹훅 URL은 **환경변수에만** 둔다. 코드/깃에 시크릿 금지(HARD).

---

## 6. 도메인 이벤트 연동

### 6.1 사업자 인증 신청 알림

```
verification/
├── event/BusinessVerificationSubmittedEvent.kt
└── listener/BusinessVerificationEventListener.kt   # @Async @TransactionalEventListener(AFTER_COMMIT)
```

- 서비스(`@Transactional`)는 `PENDING` 저장 후 `publishEvent(BusinessVerificationSubmittedEvent(...))`만 호출.
- 리스너가 메시지 포맷팅 후 `discordNotifier.notify(VERIFICATION, msg)`.
- 메시지: 신청 시각 / userId / 상호 / 사업자번호 / 대표자명 / **등록증 이미지 링크**(관리자가 클릭해 확인).

### 6.2 유저 가입 알림

```
user/ (또는 auth/) event/UserRegisteredEvent.kt + listener/UserEventListener.kt
```

- 온보딩 완료(`OnboardingService`에서 `User` 행 생성) 직후 `UserRegisteredEvent` 발행.
- 리스너 → `discordNotifier.notify(SIGNUP, msg)`. 메시지: 가입 시각 / userId / nickname / provider(소셜).
- 발행 위치는 구현 시 `OnboardingService`의 user 생성 트랜잭션 기준으로 확정.

---

## 7. 에러 코드

`verification/error/VerificationErrorCode.kt` — `E-VERIFICATION-{NNN}` 체계.

- 미인증 커뮤니티 접근 → 403
- 중복 신청(PENDING/APPROVED 존재) → 409
- 등록증 URL 소유권 위반 → 400/403

(번호는 `docs/ERROR_CODES.md` 규칙에 맞춰 구현 시 확정)

---

## 8. 테스트

- RestDocs 테스트 3개(presign · 제출 · 상태조회) → OpenAPI 자동 문서화.
- 게이팅 테스트: 미인증 시 커뮤니티 403, `APPROVED` 시 200.
- 신청 중복 409, 등록증 소유권 위반 검증.
- Discord: 이벤트 발행/리스너 단위 테스트(Notifier는 모킹). 웹훅 미설정 시 폴백 확인.
- Zonky embedded PostgreSQL, JaCoCo line 80% 게이트 통과.

---

## 9. DDL / 문서

- `docs/sql/all-tables-ddl.sql`에 `business_verifications` 테이블 + 인덱스(부분 유니크 포함) 추가.
- `docs/sql/migration/`에 마이그레이션 + 롤백 스크립트 추가(`yy-mm-dd-` 네이밍).
- `docs/DATABASE.md` 스키마 명세 갱신.
- `ddl-auto: validate`로 엔티티↔DB 정합 확인.

---

## 10. 향후 자동화 전환 경로 (참고)

이번에 `business_number`/`business_name`/`representative_name`을 받아두므로, 자동화 시 **review 단계만** 교체하면 된다:

1. 업로드 이미지 → OCR(네이버 클로바/AWS Textract)로 필드 추출 → 신청 폼 프리필
2. 제출 시 국세청 진위확인 API(`validate`) 호출 → 일치 시 `status='APPROVED'` 자동 설정
3. 실패 건만 기존 수동 검토로 폴백

테이블·엔드포인트·게이팅은 그대로 두고 검토 로직만 자동화된다.
