# HANDOFF — Hazel Server

> 각 세션은 작업 후 이 파일을 갱신한다. 다음 세션은 ROADMAP.md + 이 파일을 읽고 이어간다.

## 현재 상태

- **SPEC-SERVER-012 (설정 API) 완료** (2026-05-23).
  - value/label 설정 4종(매출/지출 카테고리·결제방식): `@MappedSuperclass LabelSetting` + `@NoRepositoryBean` 제네릭 리포지토리 + 추상 `LabelSettingService<T>`(DRY). list/add(슬러그)/update/delete, 중복 409.
  - 카드사: list(활성)/create/update(fee_rate·deposit_days)/소프트 삭제, 중복 409. 사용자 설정(bottom_nav_items jsonb) 조회·upsert. 푸시 구독 등록/해지/상태.
  - **CGLIB 프록시 이슈 수정**: 추상 베이스의 @Transactional 메서드를 `open`으로 — all-open이 @Service만 열어 상속된 final 메서드를 어드바이스 못 해 repository null이던 문제 해결.
  - 검증: **138테스트 통과(스킵 0)** — 7개 신규(label/card/prefs/push/멀티테넌시).
- **SPEC-SERVER-011 (인사이트 API) 완료** (2026-05-23).
  - 공유 읽기(테넌트 무관, 인증만): 트렌드(category/limit/offset·카테고리별 최신), 인스타 계정(activeOnly), 포스트(accountId/region/sortBy/daysAgo, account 임베드).
  - 스크랩(테넌트 격리): 토글(대상 존재 검증·레이스 안전 saveAndFlush), 메모(스크랩 후만), 맵/목록/개수.
  - 내부 API(`/internal/**`, `InternalAuthVerifier` Bearer 타이밍-세이프): 트렌드 수집(멱등 source_url + 신규 시 broadcast), 포스트 수집(멱등 shortcode), 계정 등록/수정/삭제. `BroadcastService`(전체 활성 토큰).
  - **타입 충돌 수정**: `photo_cards.tags`를 `Array<String>`(네이티브 ARRAY)로 변경 — List<String> 네이티브 ARRAY/JSON 전역 충돌 회피(insights key_points/image_urls가 List<String> JSON). [중요: 새 List<String> 컬럼은 JSON만, 배열은 Array<String> 또는 List<Int>로]
  - "읽음 처리"는 스키마 미지원으로 미구현(스크랩이 저장 메커니즘).
  - 검증: **131테스트 통과(스킵 0)** — 15개 신규(ingest 4 + read 3 + auth 3 + scrap 5).
- **SPEC-SERVER-010 (사진첩 + 태그 API) 완료** (2026-05-23).
  - 사진카드 `PhotoCard`(tags Array<String> text[], photos jsonb[{url,originalName}] — Hibernate 네이티브) CRUD + 매출(sale_id) 연동 + 매출별 조회.
  - 목록: 커서 페이지네이션(updated_at desc, page 8) + tag 포함/customerId(sales 조인) 필터(단일 네이티브 쿼리, NULL CAST).
  - presigned 업로드 타깃(S3PresignService): 소유권·최대 10장·이미지 메타 검증 + 키 생성. 사진 순서변경/1장 삭제.
  - 태그 `PhotoTag` CRUD(중복 409 — saveAndFlush로 즉시 포착, 색상 랜덤), 삭제 시 카드 tags에서 array_remove.
  - 검증: **116테스트 통과(스킵 0)** — 11개 신규(카드 7 + 태그 4). 업로드 발급·필터·cascade·멀티테넌시 포함.
- **SPEC-SERVER-009 (입금대조 API) 완료** (2026-05-23).
  - 카드 매출 입금 목록(status/cardCompany/month 필터, `DepositSpecifications`) + 단건/다건 확인 + 되돌리기 + 요약(대기/완료 건수·금액).
  - `Sale`에 `deposited_at` 매핑 추가, `SaleResponse`에 `depositedAt` 노출. 다건 확인은 `findByUserIdAndIdIn`으로 본인 매출만(타 테넌트 ID 무시).
  - 검증: **105테스트 통과(스킵 0)** — 5개 신규(목록/확인/되돌리기/요약/멀티테넌시).
- **SPEC-SERVER-008 (예약 + 캘린더 API) 완료** (2026-05-23).
  - 예약: `Reservation` CRUD + 월별/다가오는/리마인더(48h) 조회 + 제목·메모 자동완성.
  - 매출 연동: 예약→매출 전환(SaleService로 생성+sale_id 연결), 매출에 픽업 추가(고객정보 상속), 매출별 예약 조회, 픽업 완료.
  - 캘린더: `CalendarEvent` CRUD + 월 겹침 조회 + 범위 검증(`com.hazel.calendar`).
  - 스케줄 푸시: `ReservationNotificationService` — 5분마다 도달 리마인더 발송+reminder_sent 마킹(멱등), 매일 08:00 KST 당일 픽업 요약. PushService 사용(토큰 push_subscriptions 조회, 영구실패 비활성화).
  - 검증: **100테스트 통과(스킵 0)** — 14개 신규(예약 7 + 알림 3 + 캘린더 4). 전환·상속·리마인더 윈도·스케줄러·멀티테넌시 포함.
- **SPEC-SERVER-007 (고객 API) 완료** (2026-05-23).
  - `Customer` CRUD(중복 전화 409) + 등급/성별 검증 + 등급 변경 + 부분 수정.
  - `findOrCreate`((phone,user_id) 복합키, 레이스 시 재조회), 이름 부분검색(top10), 전화번호 중복 확인.
  - 고객별 매출 `GET /customers/{id}/sales`(소유권 확인 + 페이지네이션, SaleRepository 재사용).
  - 구매 통계는 sales 실시간 집계(JdbcTemplate 네이티브, 통계 컬럼 미매핑). 목록 총구매액 내림차순.
  - 검증: **86테스트 통과(스킵 0)** — 10개 신규(service 8 + HTTP 2). 통계 집계·findOrCreate·멀티테넌시 포함.
- **SPEC-SERVER-006 (지출 + 고정비 API) 완료** (2026-05-23).
  - 지출: `Expense` CRUD + 월 필터 + 자동완성(물품명/거래처/비고 빈도순). total_amount=단가*수량 서버 계산.
  - 고정비: `RecurringExpense`(days_of_week/days_of_month INT[], yearly_dates jsonb — **Hibernate 6 네이티브 @JdbcTypeCode**, validate 통과) + CRUD/토글/빠른추가.
  - this/all 분기 4종(이것만/이후 모두 × 수정/삭제) — skip 마커, 템플릿 end_date 단축.
  - 자동생성: `RecurringScheduleEvaluator`(순수 발생판정) + `RecurringExpenseGenerator`(`@Scheduled` KST 00:30, `ON CONFLICT (recurring_id,date)` 멱등). `ScheduleConfig(@EnableScheduling)`.
  - 검증: **76테스트 통과(스킵 0)** — 21개 신규(evaluator 5 + 지출 5 + 생성기 4 + 고정비 7). 멱등/skip/멀티테넌시 포함.
  - detekt constructorThreshold 10으로 상향(다필드 JPA 엔티티).
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

## 다음 할 일 — **마지막 Phase 1 SPEC**

- **SPEC-SERVER-013 (대시보드 + 통계)**: 오늘/월 집계, 다가오는 예약, 발동 리마인더, 카테고리/결제수단/채널/고객 통계(네이티브 SQL 집계). deps: 005 ✅, 006 ✅, 007 ✅
  - 원본: `~/Desktop/hazel-admin/src/lib/actions/dashboard.ts`, `statistics.ts`, `get_sales_summary`/`get_customer_stats` RPC.
  - **네이티브 SQL 집계**(JdbcTemplate): 매출 요약(total/card/naverpay/transfer/cash/count, unpaid 제외), 카테고리/결제수단/채널별 매출, 고객별 매출 통계.
  - 대시보드: 오늘/이번 달 매출·지출 합계, 다가오는 예약(ReservationService.upcoming 재사용), 발동 리마인더(triggeredReminders 재사용).
  - 통계 쿼리는 모두 user_id 바인딩(SQL 인젝션 방지·테넌트 격리). 미수(unpaid) 매출은 총매출에서 제외.
  - 완료 시 **Phase 1(M1+M2) 13개 SPEC 전부 DONE** → 앱 연동 준비 완료.
- 도메인 패턴 참고: 기존 모든 도메인. 통계는 네이티브 SQL + JdbcTemplate.
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

- 2026-05-23 — SPEC-SERVER-012 완료. 설정 API(카드사/매출·지출설정/하단바/푸시구독). 추상 서비스 @Transactional open 수정. 138테스트 통과.
- 2026-05-23 — SPEC-SERVER-011 완료. 인사이트 API(트렌드/인스타 공유 읽기·스크랩·내부 수집/브로드캐스트). photo_cards.tags Array<String> 타입충돌 수정. 131테스트 통과.
- 2026-05-23 — SPEC-SERVER-010 완료. 사진첩+태그 API(카드 CRUD·presigned 업로드·커서 페이지·태그 cascade). 116테스트 통과.
- 2026-05-23 — SPEC-SERVER-009 완료. 입금대조 API(카드 입금 목록·단건/다건 확인·되돌리기·요약). 105테스트 통과.
- 2026-05-23 — SPEC-SERVER-008 완료. 예약+캘린더 API(CRUD·매출전환·픽업·리마인더/요약 @Scheduled 푸시). 100테스트 통과.
- 2026-05-23 — SPEC-SERVER-007 완료. 고객 API(CRUD·등급·findOrCreate·고객별 매출·통계 실시간 집계). 86테스트 통과.
- 2026-05-23 — SPEC-SERVER-006 완료. 지출+고정비 API(CRUD·자동완성·this/all 분기·@Scheduled 멱등 자동생성). 76테스트 통과.
- 2026-05-23 — SPEC-SERVER-005 완료. 매출 API(CRUD·무한스크롤·필터·자동완성·미수·서버 입금계산). 첫 도메인 패턴 확립. 55테스트 통과.
- 2026-05-23 — SPEC-SERVER-004 완료. 공통 인프라(Discord 에러 리포팅·S3 presign·FCM 추상화·CORS·보안헤더). 41테스트 통과.
- 2026-05-23 — SPEC-SERVER-003 완료. JWT 인증 + refresh 회전 + BCrypt + 가입 시드 + TenantContext + /me. 28테스트 통과.
- 2026-05-23 — SPEC-SERVER-002 완료. Flyway baseline(22테이블, RLS 제거·자체 users) + 시드. Zonky 임베디드 PG로 마이그레이션 실제 적용 검증. 11테스트 통과.
- 2026-05-23 — SPEC-SERVER-001 완료. Spring Boot(Kotlin) 스켈레톤 부팅 + 헬스체크 + Swagger + ktlint/detekt 게이트. build test 통과.
- 2026-05-23 — 부트스트랩. ROADMAP 13개 SPEC(Phase1) + 1개(Phase2) 정의. 병렬 모드 활성화.
