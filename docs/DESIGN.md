# Flori Server — 설계 (SSOT)

> Flori 모바일 전환 프로그램 · 백엔드 파트 설계. 2026-05-23 작성.
> 프로그램 전체 맥락은 `~/Desktop/flori-ai/web/docs/superpowers/specs/2026-05-23-flori-mobile-migration-design.md` 참조.

## 1. 배경

기존 Flori 어드민(`~/Desktop/flori-ai/web`)은 Next.js 16 + Supabase 웹앱. 모든 비즈니스 로직이 **Next.js Server Actions**에 묶여 있어 네이티브 앱이 호출할 수 없다. 이 백엔드는 그 로직을 **REST API**로 재구현하고 자체 AWS 인프라 위에 올린다.

## 2. 전체 아키텍처

```
 Flutter 앱 (flori-ai/mobile) ──REST+JWT──→ Flori Server (이 repo)
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

Kotlin + Gradle(KTS) / Spring Boot 3.x(Java 21) / Spring Data JPA + Hibernate(jsonb·배열은 hypersistence-utils, 통계는 네이티브 SQL) / AWS RDS PostgreSQL / Flyway / Spring Security + 자체 JWT / Jakarta Validation / AWS SDK v2(S3) / Firebase Admin(FCM) / springdoc-openapi.

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

- `signup` → users INSERT(BCrypt) + 사용자별 기본 설정 시드(카테고리/결제방식) → JWT 발급.
- `login` → access JWT(짧음) + refresh token(회전, 저장/무효화).
- JWT 서명키·TTL은 환경변수. 위변조/만료 검증.
- **카카오 소셜 로그인** (SPEC-RN-015, 구현 완료): `POST /auth/oauth/kakao` — 인증코드+redirectUri → `KakaoOAuthClient`(인터페이스, 테스트 스텁 가능) → kauth.kakao.com 토큰교환 + kapi.kakao.com 프로필조회(providerId, nickname). 신규면 User(provider=KAKAO, providerId) INSERT + 기본설정 시드, 기존이면 findByProviderAndProviderId → 동일한 JWT 발급. 동시 첫 로그인 경쟁은 DataIntegrityViolationException 캐치 후 재조회로 멱등 처리. 소셜 사용자는 email/passwordHash가 null. 설정: `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` 환경변수.
- 구글 소셜 로그인: 미구현(인터페이스 분리로 향후 동일 패턴 추가 가능).

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

- S3: presigned PUT URL(짧은 만료) 발급, 소유권 검증 후. CloudFront로 서빙. (원본 R2 → S3 전환)
- 푸시: 원본 Web Push(VAPID) → **FCM**. 토큰은 push 구독 테이블에 저장. 영구실패 토큰만 비활성화.

## 10. API 계약 (앱 연동)

- springdoc-openapi로 OpenAPI 3 + Swagger UI 노출. **이것이 flori-ai/mobile이 읽는 계약의 출처.**
- 표준 에러 응답 포맷 고정(code/message). 페이지네이션/필터 규약 일관.

## 11. 테스트 / 품질

- 단위: 서비스 로직(고정비 발생 판정·스킵·통계). 통합: Testcontainers(PostgreSQL)로 리포지토리/Flyway.
- 게이트: `./gradlew build test`. ktlint/detekt.
- 멀티테넌시 격리 테스트(다른 user의 데이터 접근 차단)는 필수 케이스.

## 12. 범위 밖 (Phase 1)

- 웹앱 변경, Supabase 실데이터 행 이관, 실제 결제(IAP) 연동.
