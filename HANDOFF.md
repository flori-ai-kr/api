# HANDOFF — Hazel Server

> 각 세션은 작업 후 이 파일을 갱신한다. 다음 세션은 ROADMAP.md + 이 파일을 읽고 이어간다.

## 현재 상태

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

- **SPEC-SERVER-003 (인증)**: Spring Security + JWT(access+refresh rotation), BCrypt,
  `POST /auth/signup`(가입 시 사용자별 기본 카테고리/결제방식/카드사 시드), `/auth/login`, `/auth/refresh`, `/auth/logout`,
  JWT 필터 → `TenantContext`.
  - `users` 테이블은 V1에 이미 존재. refresh token 저장소(테이블)는 SPEC-003에서 Flyway 마이그레이션 `V3__...`로 추가.
  - 기본 시드 참고(가입 시): 원본 `~/Desktop/hazel-admin`의 기본 카테고리/결제방식/카드사 — `src/lib/actions/`와 `src/types/database.ts`의 `PRODUCT_CATEGORIES`/`PAYMENT_METHODS` 참고.
  - 엔티티 추가 시 `ddl-auto`는 `validate` 권장(스키마 SSOT는 여전히 Flyway).
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

- 2026-05-23 — SPEC-SERVER-002 완료. Flyway baseline(22테이블, RLS 제거·자체 users) + 시드. Zonky 임베디드 PG로 마이그레이션 실제 적용 검증. 11테스트 통과.
- 2026-05-23 — SPEC-SERVER-001 완료. Spring Boot(Kotlin) 스켈레톤 부팅 + 헬스체크 + Swagger + ktlint/detekt 게이트. build test 통과.
- 2026-05-23 — 부트스트랩. ROADMAP 13개 SPEC(Phase1) + 1개(Phase2) 정의. 병렬 모드 활성화.
