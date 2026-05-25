# HANDOFF — Flori Server

> 각 세션은 작업 후 이 파일을 갱신한다. 다음 세션은 ROADMAP.md + 이 파일을 읽고 이어간다.

## 현재 상태

- 🎨 **레포 셋업 + 리브랜딩 완료** (2026-05-25): MoAI-ADK 2.14.0 프레임워크 도입(`.claude`/`.moai`/`.mcp.json`), GitHub 레포 **`flori-ai-kr/server`(public)** 생성(영문 About + topic 칩 14 + 라벨 13 + .github CI/라벨러/auto-assign/CODEOWNERS/PR템플릿). **hazel → flori 전면 리네임**(패키지 `com.hazel`→**`kr.ai.flori`** 176파일, 클래스 `FloriServerApplication`, 식별자 `flori-ai-server`, 문서·SPEC 전부). 브랜치: `main`=리네임 직전 baseline(force rewind), **`dev`=기본·전체 반영**. README는 영문(형제 레포 ai/web 형식 통일).
- ✅ **SPEC-SERVER-022 (RestDocs 검증 API 문서 + 커버리지 80%, E2) 완료** (2026-05-26): onetime/backend 패턴 이식. ePages **`restdocs-api-spec` 0.19.2** + `spring-restdocs-mockmvc` → 기존 통합테스트 위에 도메인별 `*DocsTest : RestDocsSupport()` 신설(실서비스+Zonky) → **OpenAPI 107 path/120 op/22 tag** 생성 → `OpenApiConfig`가 정적 스펙 + JWT bearerAuth 병합 → `/v3/api-docs` → springdoc swagger-ui 표시(Authorize 버튼). `packages-to-scan` 더미로 컨트롤러 스캔 억제. 컨트롤러/DTO 문서 어노테이션 25파일 제거. **JaCoCo line 89.4%**(게이트 80%) `check`/CI 연결. 브랜치 `feature/SPEC-SERVER-022-restdocs`(→dev PR 예정). 명세 `.moai/specs/SPEC-SERVER-022/{spec,plan}.md`.
  - **후속(follow-up)**: ① 생성 스펙에 JWT bearer SecurityScheme 추가 → swagger-ui Authorize 버튼 복원(ePages `openapi3` security 또는 `docs()` security requirement). ② 문서화 제외 3건 보완 검토: `photo-cards/{id}/upload-targets`(S3 presign — `@TestConfiguration` fake 빈), `subscription/premium-example`(데모), `scrap-post-list`(항목 필드).
- 🔧 **유지보수/컨벤션 라운드 진행 중** (2026-05-25): SPEC-015~021. 의존성 최신화 + 기존 Java/Spring 레포(onetime/backend·batch, socc-assistant-api)의 검증 패턴을 flori(Kotlin)에 선별 이식.
  - **SPEC-SERVER-015 (Spring Boot 3.5 업그레이드) 완료** (2026-05-25): EOL된 3.4.1 → **3.5.14**, springdoc 2.7.0 → **2.8.17**. Spring Framework 6 유지(저위험). `./gradlew build test` **165테스트 통과(0 실패)**, runtimeClasspath `spring-boot-starter-web -> 3.5.14` 확인. 문서 버전표기(ARCHITECTURE/HANDOFF) 갱신. 명세 `.moai/specs/SPEC-SERVER-015/spec.md`. 후속: 4.0(Jackson3·SF7)·Kotlin 2.2+ 는 별도.
  - **SPEC-SERVER-016 (멀티테넌시 격리 자동검출 테스트, A1) 완료** (2026-05-25): `common/tenant/TenantIsolationGuardTest` — 리플렉션으로 모든 `kr.ai.flori` 리포지토리 선언 메서드가 `user_id` 격리(메서드명 UserId 또는 @Query user_id 참조)되는지 전수 검증. 비격리는 `intentionalGlobal` 화이트리스트(인증 3·스케줄러 3·자식엔티티 2·인사이트 공유콘텐츠 11)에만 허용 + 화이트리스트 자기검증(실재·실제 비격리). **첫 실행에서 `InsightRepositories.kt`(복수 파일명이라 수동검색 누락) 공유콘텐츠 11종을 자동 검출** = 가드 효과 실증. 신규 메서드가 user_id 빠뜨리면 실패. 167테스트 통과(+2). 명세 `.moai/specs/SPEC-SERVER-016/spec.md`.
  - **SPEC-SERVER-017 (BaseEntity/Auditing + 엔티티 업데이트 컨벤션, C1+C3) 완료** (2026-05-25): `common/entity/BaseEntity.kt` — `BaseCreatedEntity`(@CreationTimestamp) + `BaseEntity`(+@UpdateTimestamp). **19개 엔티티 전환**(both→BaseEntity 13, created-only→BaseCreatedEntity 6=RefreshToken/RecurringSkip/TrendArticle/PhotoTag/SubscriptionEvent/LabelSetting). 서비스 **24곳 수동 updatedAt 제거**(Hibernate가 자동 갱신) + Instant import 정리. **범위 외**: UserPreferences(updated_at만), InstagramPost(타임스탬프 없음). C3: `Sale.markDepositCompleted()/revertDeposit()` 도메인 메서드(DepositService가 호출). ddl-auto=validate 통과(컬럼 불변), 167테스트(0 실패). PATTERNS.md recipe 갱신. 명세 `.moai/specs/SPEC-SERVER-017/spec.md`.
  - **SPEC-SERVER-018 (리치 OpenAPI 어노테이션, E1) 완료** (2026-05-25): `OpenApiConfig`에 **JWT bearer 보안 스킴 전역 등록**(Swagger Authorize 버튼) + `ErrorResponse`·`AuthDtos`·`SaleDtos`(요청 + 서버계산 SSOT 필드)에 `@Schema`(설명/예시/허용값). 나머지 DTO는 동일 패턴 점진 적용. 함정: Kotlin 블록주석 중첩 → KDoc에 `/webhooks/**` 같은 글롭이 "Unclosed comment" 유발(수정). 167테스트 통과. 명세 `.moai/specs/SPEC-SERVER-018/spec.md`.
  - **SPEC-SERVER-019 (스케줄러 멱등성 + 실패격리, D1+D2) 완료** (2026-05-25): `V5__notification_log.sql`(user_id·type·dedup_key UNIQUE) + `ReservationNotificationService.claimOnce()`(INSERT ON CONFLICT DO NOTHING, 갱신행수==1) → 일일요약 at-most-once. **건별 격리**(D2): 리마인더·일일요약·고정비생성에서 메서드 `@Transactional` 제거(PG는 tx 내 1문장 오류 시 전체 abort → 독립 커밋으로 격리) + 건별 `catch(DataAccessException)`. 잡 레벨 격리는 Spring 스케줄러가 메서드별 제공(래퍼 불요). 일일요약 멱등성 테스트 추가(2회 호출→2번째 0건). 168테스트 통과(+1). 명세 `.moai/specs/SPEC-SERVER-019/spec.md`.
  - **SPEC-SERVER-020 (에러리포팅 PII 마스킹+truncate, B4) 완료** (2026-05-25): `DiscordReporting.kt` 공통 `maskSensitive`(경로/이메일/**전화번호**/토큰/비밀번호/키) 추출 → `sanitizeStack`(+줄제한)·`sanitizeMessage`(단문용). `DiscordErrorReporter.buildPayload`의 **메시지·액션 필드**에 `sanitizeMessage`+truncate 적용(기존엔 메시지 truncate만·액션 무방비). 전화번호 `\d{2,3}-\d{3,4}-\d{4}`→[PHONE](날짜 미매칭). 테스트 +2. 170테스트 통과. 명세 `.moai/specs/SPEC-SERVER-020/spec.md`.
  - **SPEC-SERVER-021 (컨벤션 ADR 문서 체계, DOC1) 완료** (2026-05-25): `docs/conventions/` 신설 — `README.md`(인덱스 + 작성규칙 `yy-mm-dd-{슬러그}.md`, 구조: 배경/결정/근거/공식링크/적용범위, SUPERSEDED 이력 보존) + ADR 2건: `2026-05-25-multitenancy-isolation.md`, `2026-05-25-entity-auditing-and-update-convention.md`. PATTERNS.md 인트로에서 링크. 출처: socc-assistant-api docs/conventions. 명세 `.moai/specs/SPEC-SERVER-021/spec.md`.
  - 🎉 **유지보수/컨벤션 라운드(SPEC-015~021) 전부 DONE** — 다음 TODO 없음.
- 🎉 **전체 15개 기능 SPEC DONE** (Phase 1: 13 + Phase 2: 1 + 품질개선 RF-001, 2026-05-23).
- **SPEC-SERVER-RF-001 (리팩터링 & 품질) 완료** (2026-05-23). AUDIT → REFACTOR → DOCUMENT, 동작 보존.
  - **AUDIT**: 4차원 점검(`.moai/specs/SPEC-SERVER-RF-001/audit.md`) + 독립 code-reviewer 교차검증. **멀티테넌시 user_id 격리 전수 재확인 = 누락 0건**(findById/findAll/existsById/네이티브 SQL 전수, 의도적 전역/cross-tenant 식별).
  - **REFACTOR**(behavior-preserving 4건): R1 `monthRange` 잘못된 입력 500→400(VALIDATION)+테스트 / R2 `ReservationStatuses` → `common/domain` 추출 / R3 `SettingsServices.kt` → 3파일 분리 / R4 교차도메인 접근 주석(`DepositCalculator`·`UserPreferenceService`).
  - **보류**(동작 변경이라 별도 SPEC): `deleteRecurringFromInstance` 스킵행(ON CONFLICT가 이미 방지)·`BroadcastService` LIMIT·`InsightService` region JPQL 푸시다운.
  - 문서: `audit.md` + `refactor-log.md` + `docs/PATTERNS.md` 공통상수 표 갱신.
  - 검증: **165테스트 통과(스킵 0)** — 6개 신규(DateRangesTest).
- **SPEC-SERVER-014 (구독/결제) 완료** (2026-05-23). **Phase 2 첫/유일 SPEC.**
  - `V4__subscriptions.sql`: `subscriptions`(user_id UNIQUE, 사용자당 1행) + `subscription_events`(append-only 이력, raw_event jsonb).
  - `POST /webhooks/revenuecat`: 사전 공유 Bearer 시크릿(`RevenueCatWebhookVerifier`, 타이밍-세이프) → 이벤트 매핑 → upsert. 항상 200 ACK(재시도 폭주 방지). SecurityConfig `/webhooks/**` 공개.
  - 상태 매핑 `SubscriptionStatusMapper`(순수): 구매/갱신/상품변경/취소복원/취소→active, BILLING_ISSUE→in_grace, EXPIRATION→expired, REFUND→none.
  - `GET /subscription`(현재 상태 DTO, OpenAPI 노출) + `@RequiresSubscription` 어노테이션 + `SubscriptionAccessInterceptor`(ObjectProvider 지연주입 — 슬라이스 안전) 게이팅. 비구독 403.
  - 중복 제거: 내부 API/웹훅 Bearer 검증을 `common/security/BearerSecret`로 공통화(`InternalAuthVerifier`도 이를 재사용).
  - 검증: **159테스트 통과(스킵 0)** — 14개 신규(매퍼 6 + HTTP 8: 전이/취소/none/게이팅403/웹훅인증/격리/무토큰).
- 🎉 **Phase 1 (M1 기반 + M2 도메인) 13개 SPEC 전부 DONE** (2026-05-23). 백엔드 REST API 완성 — 앱 연동 준비 완료.
- **보안/클린아키텍처 리뷰 반영 완료** (2026-05-23): security-auditor + code-reviewer 병렬 리뷰 후 수정. **145테스트 통과(스킵 0).**
  - 보안: 푸시 구독 테넌트 격리(findByEndpoint 전역조회 제거), 예약·사진 `saleId` 소유권 검증(IDOR 차단), JWT 기본 시크릿 운영 부팅 가드 + alg-confusion 테스트, actuator 공개 범위 축소(health/info), 내부 키 비교 길이-무관 다이제스트화.
  - 클린아키텍처: `SaleService` 고객검증 raw JDBC→`CustomerRepository`, `DepositService.summary` 네이티브 집계화, `sendDailySummary` readOnly→read-write, 시더 테이블명 allowlist, `InternalAuthVerifier`→`common/security` 이동, `ReservationService` 직접 `SaleRepository` 제거.
  - 유지보수: `monthRange`/`KST`/`PaymentMethods`/`DepositStatuses` 공통화(중복 제거).
  - **의도적 유지(리뷰 반론)**: `completeUnpaid`의 `is_unpaid=true`는 원본 의미 충실(현재상태=payment_method, 합계는 'unpaid' 제외 → 중복집계 없음). 레이트리밋·prod Swagger 비활성·CORS credentials는 배포 단계 항목.
- **문서 세트 추가** (2026-05-23): `README.md`(개요·Quick Start·환경변수·구조·문서 인덱스), `docs/PATTERNS.md`(레이어/멀티테넌시/DTO/에러/트랜잭션 패턴 + 새 도메인 추가 레시피 + 컨벤션), `docs/KOTLIN.md`(Kotlin/Spring 관용구 입문자용 + all-open·엔티티·@field: 함정). 기존 `docs/ARCHITECTURE.md`와 상호 링크.
- **SPEC-SERVER-013 (대시보드 + 통계) 완료** (2026-05-23). **마지막 SPEC.**
  - 오늘 대시보드 `GET /dashboard/today`: 미수 제외 매출 요약 + 다가오는 예약 + 발동 리마인더 + 최근 매출 5건 + 매출 카테고리(기존 서비스 재사용).
  - 월 통계 `GET /dashboard/month`: 매출/지출 요약 + 카테고리/결제수단/채널/지출 통계(금액·비율) + 고객 통계(총/재방문/신규).
  - **네이티브 SQL 집계**(JdbcTemplate, Postgres FILTER/GROUP BY/EXISTS), 모든 쿼리 user_id 바인딩(격리·인젝션 방지), 미수 제외.
  - 검증: 4개 신규(오늘/월/비율합/멀티테넌시).
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
  - 캘린더: `CalendarEvent` CRUD + 월 겹침 조회 + 범위 검증(`kr.ai.flori.calendar`).
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
  - Gradle(KTS) + Spring Boot 3.5.14 + Kotlin 2.1.0, Java 21 toolchain, Gradle Wrapper 8.11.1 동봉.
  - 패키지: `kr.ai.flori`(메인) + `kr.ai.flori.common`(config/health). 도메인 패키지는 후속 SPEC에서 추가.
  - `GET /health` → `HealthResponse{status,service,time}` (DB 비의존). Actuator `/actuator/health` 포함.
  - springdoc-openapi(`/swagger-ui.html`, `/v3/api-docs`) + OpenAPI 메타.
  - 품질 게이트: ktlint 1.5.0(official) + detekt 1.23.7. `./gradlew build test` 통과(테스트 2개).
  - 멀티스테이지 `Dockerfile`(temurin 21).
- **병렬 모드**: `~/Desktop/flori-ai/mobile`과 동시 진행. 백엔드는 앱을 기다리지 않고 독립 실행.

## 다음 할 일 — Phase 1 완료

- **Phase 1 전부 완료.** ROADMAP의 다음 TODO는 **SPEC-SERVER-014 (구독/결제)** — `deps: (앱 M4 완료)` 이므로 **현재 진행 불가**(앱 출시 후 Phase 2). 자율 루프는 진행 가능한 TODO가 없어 여기서 멈춘다.
- **남은 운영 준비물(사용자 작업)**: 배포 환경변수 — `DB_URL`/`DB_USER`/`DB_PASSWORD`, `JWT_SECRET`, `AWS_*`/`S3_BUCKET`/`CLOUDFRONT_DOMAIN`, `FCM_ENABLED`/`FCM_CREDENTIALS`, `DISCORD_WEBHOOK_URL`, `INTERNAL_API_KEY`, `CORS_ALLOWED_ORIGINS`. (코드는 모두 `${ENV}` 참조, 미설정 시 로컬 graceful 동작.)
- **앱 연동**: Swagger UI(`/swagger-ui.html`) / OpenAPI(`/v3/api-docs`)가 계약 출처. flori-ai/mobile(Flutter)이 이 ROADMAP의 DONE 상태를 보고 연동.
- **후속(선택)**: 통합 e2e 스모크, 실제 RDS/Testcontainers 기반 CI, 부하/보안 점검.
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

- 2026-05-23 — 🎉 **Phase 1 완료(13/13 SPEC)**. SPEC-SERVER-013 완료. 대시보드+통계(네이티브 SQL 집계). 142테스트 통과. Phase 2(결제)는 앱 M4 완료 후.
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

## [활성화] Phase 2 결제 (2026-05-23)
- 사용자 A 선택. **SPEC-014(결제/구독) 진행 — 코드 구현만**(실계정/배포/키 없음, env placeholder, 모의 테스트).
- loop 재시작 필요(Phase1 완료로 정지했었음).

## [지시] 리팩터링 & 문서화 (2026-05-23)
- 사용자 지시. **다음 TODO = RF-001**: 4차원(클린아키텍처/보안/확장성/유지보수성) audit → 동작보존 리팩터 → 문서화. loop 재시작 필요.
