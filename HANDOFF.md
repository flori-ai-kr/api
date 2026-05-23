# HANDOFF — Hazel Server

> 각 세션은 작업 후 이 파일을 갱신한다. 다음 세션은 ROADMAP.md + 이 파일을 읽고 이어간다.

## 현재 상태

- **SPEC-SERVER-005 (매출 API) 완료** (2026-05-23). **첫 도메인 SPEC — 레이어/멀티테넌시 패턴 확립.**
  - `Sale` 엔티티 + `SaleRepository`(JpaSpecificationExecutor) + `SaleSpecifications`(동적 필터).
  - CRUD + 무한스크롤(offset/limit, hasMore) + 다중선택 필터(category/payment/channel IN, month 연/월/일, search ILIKE) + 비고 자동완성(빈도순).
  - **서버 계산(SSOT)**: `DepositCalculator`/`DepositMath` — 카드 fee=round(amount*rate/100), expected=amount-fee, 입금예정일=+N영업일(주말 제외), pending. 비카드 not_applicable.
  - 미수: is_unpaid(영구 마커) + `/complete-unpaid`·`/revert-unpaid`.
  - 모든 쿼리 `TenantContext.currentUserId()` 격리. customer_id 제공 시 소유권 검증.
  - 검증: **55테스트 통과(스킵 0)** — 14개 신규(math 3 + service 7 + HTTP 4). 멀티테넌시 격리(서비스·HTTP) 포함.
  - detekt 임계값 조정(LongParameterList 8, TooManyFunctions 20 — 도메인 API 특성 반영).
- **SPEC-SERVER-004 (공통 인프라) 완료** (2026-05-23).
  - 에러: `GlobalExceptionHandler` 확장(검증/제약/DataIntegrity→409/AccessDenied→403/일반→500) + `DiscordErrorReporter`(@Async, 미설정 시 콘솔 폴백, 5분 dedup, 스택/PII 새니타이즈). `AsyncConfig(@EnableAsync)`.
  - 스토리지: `S3PresignService.presignUpload`(presigned PUT + CloudFront/S3 파일 URL), `S3Presigner` 빈(지연 자격증명, 무자격증명 부팅 OK).
  - 푸시: `PushService` 추상화 + `FirebasePushService`(fcm.enabled=true) + `LoggingPushService`(폴백, ConditionalOnMissingBean).
  - CORS(origin 화이트리스트 env) + 보안헤더(Referrer-Policy 추가, DENY/nosniff 기본) → SecurityConfig 통합.
  - 검증: **41테스트 통과(스킵 0)** — Discord 새니타이즈/리포팅·시크릿 비노출, S3 presign 오프라인, CORS 프리플라이트, 보안헤더, 푸시 폴백.
- **SPEC-SERVER-003 (인증) 완료** (2026-05-23).
  - Spring Security 무상태 + 자체 JWT(HS256, access 15분) + refresh 회전(불투명 난수, DB에 SHA-256 해시 저장) + BCrypt.
  - `V3__refresh_tokens.sql` 추가. 엔티티 `User`/`RefreshToken`, `ddl-auto=validate`로 전환.
  - `common/security`(JwtTokenProvider/Filter/SecurityConfig/JwtProperties/UserPrincipal), `common/tenant`(TenantContext ThreadLocal), `common/error`(경량 ErrorCode/AppException/ErrorResponse/GlobalExceptionHandler).
  - `auth`: signup(+기본설정 시드)/login/refresh/logout + 보호 엔드포인트 `GET /me`.
  - 가입 시드: 매출카테고리11·매출결제4·지출카테고리7·지출결제3·카드사9(JdbcTemplate, 멱등). saveAndFlush로 같은 트랜잭션 FK 보장.
  - 검증: Zonky 임베디드 PG + 보안필터 전체 흐름. **28테스트 통과(스킵 0)**.
- **SPEC-SERVER-002 (DB + Flyway baseline) 완료** (2026-05-23).
  - JPA + Flyway(core+postgresql) + PostgreSQL 드라이버 + hypersistence-utils 추가.
  - `application.yml`: datasource/JPA/Flyway 전부 `${ENV}` 참조, `ddl-auto=none`, `open-in-view=false`.
  - `V1__init_schema.sql`: 원본 Supabase 스키마 이식. RLS 전부 제거 + `auth.users` FK 제거 + 자체 `users` 테이블, 모든 `user_id`→`users(id)` ON DELETE CASCADE. 총 22테이블(users + 21). jsonb/배열/uuid/timestamptz·복합 unique 유지. 드리프트 반영(sales.is_unpaid, reservations.reminder_sent/pickup_completed, recurring_expenses 다중값, expenses.category CHECK 제거, calendar_events).
  - `V2__seed_instagram_accounts.sql`: 공유 계정 15건 시드.
  - 테스트: **Zonky embedded-postgres**(Docker 불필요, 실제 PG)로 마이그레이션 실제 적용 검증 + DB 무관 SQL 규칙 테스트. 전체 11테스트 통과(스킵 0).
- **SPEC-SERVER-001 (프로젝트 스켈레톤) 완료** (2026-05-23).
  - Gradle(KTS) + Spring Boot 3.4.1 + Kotlin 2.1.0, Java 21 toolchain, Gradle Wrapper 8.11.1 동봉.
  - 패키지: `com.hazel`(메인) + `com.hazel.common`(config/health). 도메인 패키지는 후속 SPEC에서 추가.
  - `GET /health` → `HealthResponse{status,service,time}` (DB 비의존). Actuator `/actuator/health` 포함.
  - springdoc-openapi(`/swagger-ui.html`, `/v3/api-docs`) + OpenAPI 메타.
  - 품질 게이트: ktlint 1.5.0(official) + detekt 1.23.7. `./gradlew build test` 통과(테스트 2개).
  - 멀티스테이지 `Dockerfile`(temurin 21).
- **병렬 모드**: `~/Desktop/hazel-app`과 동시 진행. 백엔드는 앱을 기다리지 않고 독립 실행.

## 다음 할 일

- **SPEC-SERVER-006 (지출 + 고정비 API)**: 지출 CRUD + 자동완성, 고정비(recurring) CRUD(this/future/all 분기) + 빠른추가,
  `@Scheduled` KST 00:30 고정비 자동 생성(recurring_skips 고려). deps: 004 ✅
  - 원본: `~/Desktop/hazel-admin/src/lib/actions/expenses.ts`, `recurring-expenses.ts`. 지출 총액 = unit_price*quantity(서버 계산).
  - 고정비 다중값(days_of_week/days_of_month/yearly_dates) → expenses 자동생성, `(recurring_id,date)` unique로 멱등.
  - 스케줄 자동생성은 `@Scheduled` + 멱등 INSERT. 테스트는 생성 로직을 직접 호출(스케줄 트리거 분리).
  - 패턴은 SPEC-005 매출(Sale*) 구조 그대로 따른다. 모든 쿼리 `TenantContext` 격리.
- 도메인 패턴 참고: `com.hazel.sales`(엔티티/Repository+Specification/Service[TenantContext]/Controller/DTO, Zonky 통합테스트 + 순수 단위테스트).
- [중요] SPEC 완료 시 ROADMAP status를 `DONE`으로 정확히 갱신 — 앱 세션이 이 상태를 보고 연동을 시작한다.

## 빌드/실행 메모

- 게이트: `./gradlew build test` (ktlint + detekt + 테스트 포함).
- 포맷 자동 교정: `./gradlew ktlintFormat`.
- detekt는 main 소스셋만 분석. ktlint 엔진은 1.5.0(Kotlin 2.1 호환)으로 고정.
- detekt 1.23.7은 Kotlin 2.0.10 컴파일 → `detekt` 컨피그의 kotlin 의존성을 2.0.10으로 고정해 충돌 회피(build.gradle.kts 참조).

## 사전 준비물 (실제 동작에 필요 — 구현과 별개로 사용자가 채울 환경변수)

코드는 `${ENV}` 참조로 작성하고, 실제 값은 배포 시 주입한다. 로컬은 docker-compose Postgres로 대체 가능.

- `DB_URL`, `DB_USER`, `DB_PASSWORD` (AWS RDS PostgreSQL) — SPEC-002부터 필요
- `JWT_SECRET` (서명키) — SPEC-003
- `AWS_REGION`, `S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `CLOUDFRONT_DOMAIN` — SPEC-004
- `FCM` 서비스 계정 JSON 경로/내용 — SPEC-004
- `DISCORD_WEBHOOK_URL` — SPEC-004
- `INTERNAL_API_KEY` (≥32자) — SPEC-011

## 블로커

- 없음.

## 로그 (최신이 위로)

- 2026-05-23 — SPEC-SERVER-005 완료. 매출 API(CRUD·무한스크롤·필터·자동완성·미수·서버 입금계산). 첫 도메인 패턴 확립. 55테스트 통과.
- 2026-05-23 — SPEC-SERVER-004 완료. 공통 인프라(Discord 에러 리포팅·S3 presign·FCM 추상화·CORS·보안헤더). 41테스트 통과.
- 2026-05-23 — SPEC-SERVER-003 완료. JWT 인증 + refresh 회전 + BCrypt + 가입 시드 + TenantContext + /me. 28테스트 통과.
- 2026-05-23 — SPEC-SERVER-002 완료. Flyway baseline(22테이블, RLS 제거·자체 users) + 시드. Zonky 임베디드 PG로 마이그레이션 실제 적용 검증. 11테스트 통과.
- 2026-05-23 — SPEC-SERVER-001 완료. Spring Boot(Kotlin) 스켈레톤 부팅 + 헬스체크 + Swagger + ktlint/detekt 게이트. build test 통과.
- 2026-05-23 — 부트스트랩. ROADMAP 13개 SPEC(Phase1) + 1개(Phase2) 정의. 병렬 모드 활성화.
