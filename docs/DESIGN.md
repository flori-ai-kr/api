# Flori API — 설계 (SSOT)

> Flori 모바일 전환 프로그램 · 백엔드 파트 설계. 2026-05-23 작성.
> 프로그램 전체 맥락은 `~/Desktop/flori-ai/web/docs/superpowers/specs/2026-05-23-flori-mobile-migration-design.md` 참조.

## 1. 배경

기존 Flori 어드민(`~/Desktop/flori-ai/web`)은 Next.js 16 + Supabase 웹앱. 모든 비즈니스 로직이 **Next.js Server Actions**에 묶여 있어 네이티브 앱이 호출할 수 없다. 이 백엔드는 그 로직을 **REST API**로 재구현하고 자체 AWS 인프라 위에 올린다.

## 2. 전체 아키텍처

```
 React Native 앱 (flori-ai/mobile) ──REST+JWT──→ Flori API (이 repo)
                                       ├ Spring Security + JWT
                                       ├ @Scheduled (Cron 대체)
                                       ├ S3 presigned 발급
                                       ├ FCM 푸시
                                       └ @ControllerAdvice + Discord
                                              │
                              ┌───────────────┼────────────────┐
                          AWS RDS         AWS S3+CloudFront    FCM
                        (PostgreSQL)        (이미지)         (Firebase)
```

기존 Next.js 웹은 당분간 Supabase 위에서 그대로 동작(미연결). 통합/데이터 이관은 후속 Phase.

## 3. 기술 스택

Kotlin + Gradle(KTS) / Spring Boot 3.x(Java 21) / Spring Data JPA + Hibernate(jsonb·배열은 hypersistence-utils, 통계는 네이티브 SQL) / AWS RDS PostgreSQL(DDL 직접 관리 `docs/sql`, Flyway 미사용) / Spring Security + 자체 JWT / Jakarta Validation / AWS SDK v2(S3) / Firebase Admin(FCM) / springdoc-openapi.

## 4. 레이어 / 패키지 구조

```
kr.ai.flori
├── common/
│   ├── config/      (Security, CORS, OpenAPI, Jackson, Async/Schedule)
│   ├── security/    (JWT provider, 필터, UserDetails)
│   ├── tenant/      (TenantContext — 요청 스코프 userId)
│   ├── error/       (AppException, ErrorCode, GlobalExceptionHandler, Discord reporter)
│   ├── storage/     (S3 presigned 서비스)
│   └── push/        (FCM 서비스)
├── auth/            (controller/service/dto/entity)
├── sales/
├── expenses/        (지출 + 고정비)
├── customers/
├── reservations/    (예약 + 캘린더)
├── photos/          (사진카드 + 태그)
├── insights/        (트렌드/인스타/스크랩 + 내부 API)
├── settings/        (매출설정/지출설정/사용자설정/푸시구독)
└── dashboard/       (대시보드 + 통계)
```

레이어: `controller → service → repository`. 엔티티는 서비스 안에서만, 컨트롤러는 DTO만.

## 5. 멀티테넌시 (보안 핵심)

- 원본은 Postgres RLS(`auth.uid() = user_id`)로 격리. 이 백엔드는 RLS 없음 → **애플리케이션이 유일한 방어선**.
- JWT의 `sub`(userId) → `TenantContext`(요청 스코프) → 모든 조회/변경 쿼리에 `user_id` 조건 강제.
- 권장: Hibernate `@Filter`를 요청마다 활성화하거나, 공통 베이스 리포지토리에서 `userId` 파라미터 강제. 코드 리뷰에서 누락 점검.
- 복합 unique 제약 유지: `(phone,user_id)`, `(value,user_id)` 등.

## 6. 인증

- **소셜 전용 인증**: 이메일/비밀번호 가입은 폐지(비밀번호 미저장). 카카오/구글/네이버 OAuth로만 로그인한다.
- `POST /auth/oauth/{kakao|google|naver}` → `SocialOAuthClient`(provider별 빈, 테스트 스텁 가능)로 제공자 토큰교환 + 프로필조회(providerId, socialEmail, nickname). 카카오는 `kakao_account.email`을 읽어 `socialEmail`로 전달해 온보딩 이메일 프리필에 사용한다(동의항목 미수집 시 null). 기존 신원이면 즉시 JWT 발급(`registered=true`), 신규 신원이면 **User를 만들지 않고** `registerToken`(JWT 5분)만 발급(`registered=false`, socialEmail 동봉).
- `POST /auth/register/complete` → registerToken 검증(신원은 토큰에서만 도출) + 가게 프로필 + 기본설정 시드(카테고리/결제방식)와 함께 **User + user_profiles 행을 한 트랜잭션에서 생성** → JWT 발급. **즉, User 존재 = 온보딩 완료 = 가입 완료** (별도 onboarded 플래그 없음).
- `POST /auth/refresh` → access JWT(짧음) + refresh token 회전(저장/무효화). JWT 서명키·TTL은 환경변수. 위변조/만료 검증.
- 동시 첫 가입 경쟁은 DataIntegrityViolationException 캐치로 멱등 처리(중복 신원/이메일 → DUPLICATE). 설정: `KAKAO_*`, `GOOGLE_*`, `NAVER_*` 환경변수.

## 7. 도메인 매핑 (원본 Server Actions → API)

원본 `~/Desktop/flori-ai/web/src/lib/actions/`의 함수들을 REST 리소스로 옮긴다. 비즈니스 규칙은 원본 그대로:
- 지출 총액 `unit_price * quantity`
- 고정비: 주/월/연 + 다중 일자, recurring_skips, `(recurring_id,date)` unique, this/future/all 분기
- 미수: `payment_method='unpaid' + is_unpaid=true`, 완료/되돌리기
- 사진: presigned PUT 직접 업로드(소유권/메타 검증), 카드당 최대 10장
- 다중선택 필터: category/payment/channel = 배열 → SQL IN
- 인사이트 스크랩: polymorphic(target_type+target_id), `(user_id,target_type,target_id)` unique

상세 필드는 원본 `docs/ARCHITECTURE.md`의 ERD/타입 정의 참조.

## 8. 스케줄 (Vercel Cron → @Scheduled)

- 일일 예약 요약 푸시 (08:00 KST)
- 개별 예약 리마인더 (reminder_at 도달분)
- 고정비 자동 생성 (00:30 KST, recurring_expenses → expenses, skips 제외, 멱등)

## 9. 스토리지 / 푸시

- S3: presigned PUT URL(짧은 만료) 발급, 소유권 검증 후. CloudFront로 서빙. presigned GET으로 원본 다운로드. 삭제 시 S3 객체도 best-effort 정리. (원본 R2 → S3 전환)
- 푸시: **FCM**(모바일) + **Web Push/VAPID**(브라우저 PWA). `PushDispatcher`가 구독의 p256dh/auth 유무로 전송 경로를 분기. 영구실패 구독만 비활성화.

## 10. API 계약 (앱 연동)

- springdoc-openapi로 OpenAPI 3 + Swagger UI 노출. **이것이 flori-ai/mobile이 읽는 계약의 출처.**
- 표준 에러 응답 포맷 고정(code/message). 페이지네이션/필터 규약 일관.

## 11. 테스트 / 품질

- 단위: 서비스 로직(고정비 발생 판정·스킵·통계). 통합: Zonky 임베디드 PostgreSQL(`spring.sql.init`로 `docs/sql` DDL 적용)로 리포지토리.
- 게이트: `./gradlew build test`. ktlint/detekt.
- 멀티테넌시 격리 테스트(다른 user의 데이터 접근 차단)는 필수 케이스.

## 12. 범위 밖 (Phase 1)

- 웹앱 변경, Supabase 실데이터 행 이관, 실제 결제(IAP) 연동.
