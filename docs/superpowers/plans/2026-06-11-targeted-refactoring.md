# 타겟 리팩터링 + 테스트 인프라 강화 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 동작 보존을 전제로, 서비스 레이어에 박힌 네이티브 SQL을 repository 레이어(QueryRepository)로 이동하고, 서비스 책임을 정리하고, 페이지네이션을 표준화하고, 테스트 인프라(픽스처·리포지토리 테스트)를 구축한다.

**Architecture:** 스펙은 `docs/superpowers/specs/2026-06-11-targeted-refactoring-design.md`. 모든 작업은 behavior-preserving — API 계약 무변경(기존 RestDocs 테스트 무수정 통과), DB 스키마 무변경, SQL 문장 무변경(위치만 이동). 각 태스크 완료 시 `./gradlew build test` 통과 후 커밋.

**Tech Stack:** Kotlin + Spring Boot 3.5, JdbcTemplate, Spring Data JPA, Zonky embedded PostgreSQL, JUnit5 + MockMvc.

**범위 확정 노트:** statistics 4개 서비스와 DashboardService는 SQL 이동 대상에서 **제외**한다 — 이들은 비즈니스 로직 없이 SQL+DTO 조립만 하는 읽기 전용 전용 클래스라서 이미 사실상 query-object 역할을 하고 있고, 이동하면 레이어만 하나 늘 뿐 얻는 게 없다. 진짜 문제는 비즈니스 로직과 SQL이 **섞인** CustomerService·SaleService다. (이 판단을 리팩터링 기록 문서에 명시할 것.)

---

### Task 1: 테스트 픽스처 빌더 + 테넌트 헬퍼

**Files:**
- Create: `src/test/kotlin/kr/ai/flori/support/Tenants.kt`
- Create: `src/test/kotlin/kr/ai/flori/support/Fixtures.kt`

기존 서비스 테스트(`src/test/kotlin/kr/ai/flori/*/service/*.kt`)가 TenantContext를 어떻게 설정하는지 먼저 확인하고 그 패턴을 따른다.

- [ ] **Step 1: 기존 서비스 테스트의 TenantContext 설정 패턴 확인**

Run: `grep -rn "TenantContext" src/test/kotlin --include="*.kt" | head -20`

- [ ] **Step 2: Tenants 헬퍼 작성** — 특정 테넌트로 블록 실행 + 정리 보장

```kotlin
package kr.ai.flori.support

import kr.ai.flori.common.tenant.TenantContext

/** 테스트에서 특정 테넌트 컨텍스트로 블록을 실행하고 반드시 정리한다. */
object Tenants {
    fun <T> runAs(userId: Long, block: () -> T): T {
        TenantContext.set(userId)   // 실제 set 메서드명은 TenantContext.kt 확인 후 일치시킬 것
        return try {
            block()
        } finally {
            TenantContext.clear()
        }
    }
}
```

- [ ] **Step 3: Fixtures 작성** — Sale/Customer 엔티티 기본값 빌더 (FK 미사용 스키마이므로 라벨 id는 임의값 허용. 단, 라벨 해석이 필요한 테스트는 LabelSettingService로 실제 라벨 생성)

```kotlin
package kr.ai.flori.support

import kr.ai.flori.customers.entity.Customer
import kr.ai.flori.sales.entity.Sale
import java.time.LocalDate

/** 도메인 엔티티 테스트 픽스처. 합리적 기본값 + 필요한 것만 오버라이드. */
object Fixtures {
    fun sale(
        userId: Long,
        date: LocalDate = LocalDate.of(2026, 6, 1),
        categoryId: Long = 1L,
        amount: Long = 50_000L,
        paymentMethodId: Long? = 1L,
        customerId: Long? = null,
    ): Sale =
        Sale(userId = userId, date = date, categoryId = categoryId, amount = amount, paymentMethodId = paymentMethodId)
            .apply { this.customerId = customerId }

    fun customer(
        userId: Long,
        name: String = "테스트고객",
        phone: String = "01000000000",
    ): Customer = Customer(userId, name, phone)
}
```

(엔티티 생성자 시그니처는 작성 시점에 `Sale.kt`/`Customer.kt`를 열어 정확히 맞출 것. var 프로퍼티는 apply로.)

- [ ] **Step 4: 컴파일 확인** — `./gradlew compileTestKotlin` 통과
- [ ] **Step 5: 커밋** — `test: 테스트 픽스처 빌더(Fixtures)·테넌트 헬퍼(Tenants) 추가`

---

### Task 2: CustomerQueryRepository 추출 (SQL 이동 + mapper 중복 제거) + 직접 테스트

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/customers/repository/CustomerQueryRepository.kt`
- Modify: `src/main/kotlin/kr/ai/flori/customers/service/CustomerService.kt` (SQL 4개 + mapper 제거, jdbcTemplate 의존 제거)
- Test: `src/test/kotlin/kr/ai/flori/customers/repository/CustomerQueryRepositoryTest.kt`

- [ ] **Step 1: 실패하는 테스트 먼저 작성** — 새 클래스 API 기준 (클래스가 없으므로 컴파일 실패가 곧 RED)

테스트 케이스 (Zonky embedded + @SpringBootTest, 데이터는 Fixtures + 실제 repository save):
1. `statsFor` — 매출 2건 저장 후 cnt=2, total=합계, first/last date 검증
2. `aggregateStats` — 고객 2명 각각 매출 저장 후 customer_id별 맵 검증
3. `purchaseCounts` — customer_id별 건수 맵 검증
4. `photoSummary(userId)` / `photoSummary(userId, customerId)` — photo_cards 데이터 기반 썸네일·카운트 (photo_cards 적재는 PhotoCard 엔티티 확인 후 작성)
5. **테넌트 격리** — 다른 userId 데이터가 결과에 섞이지 않음 (모든 메서드 공통)

- [ ] **Step 2: CustomerQueryRepository 구현** — CustomerService.kt:281-380의 SQL을 **그대로** 이동

```kotlin
package kr.ai.flori.customers.repository

import kr.ai.flori.customers.dto.CustomerStats
import kr.ai.flori.customers.dto.PhotoThumbnail
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 고객 도메인의 네이티브 SQL 집계 전용 리포지토리.
 * 구매 통계는 sales 실시간 집계(SSOT), 썸네일은 photo_cards jsonb 첫 요소.
 */
@Repository
class CustomerQueryRepository(private val jdbcTemplate: JdbcTemplate) {
    fun statsFor(userId: Long, customerId: Long): CustomerStats { /* CustomerService.kt:344-362 SQL 그대로 */ }
    fun aggregateStats(userId: Long): Map<Long, CustomerStats> { /* :364-380 그대로 */ }
    fun purchaseCounts(userId: Long): Map<Long, Int> { /* :72-79 그대로 */ }
    fun photoSummaryByCustomer(userId: Long): Map<Long, Pair<List<PhotoThumbnail>, Int>> { /* :281-309 */ }
    fun photoSummaryFor(userId: Long, customerId: Long): Pair<List<PhotoThumbnail>, Int> { /* :312-342 */ }

    /** 복붙 2벌이던 썸네일 배열 매핑을 1벌로 통합. */
    private fun mapThumbnails(urlArr: Array<*>?, idArr: Array<*>?): List<PhotoThumbnail> { ... }
}
```

- [ ] **Step 3: CustomerService에서 위임으로 교체** — private SQL 메서드 5개 삭제, `jdbcTemplate` 생성자 파라미터 제거, 호출부를 `queryRepository.*`로
- [ ] **Step 4: 테스트 실행** — `./gradlew test --tests "kr.ai.flori.customers.*"` PASS
- [ ] **Step 5: 전체 빌드** — `./gradlew build test` PASS (기존 Customer RestDocs·통합 테스트 무수정 통과 확인)
- [ ] **Step 6: 커밋** — `refactor(customers): 네이티브 SQL을 CustomerQueryRepository로 이동(레이어 정합) + 직접 테스트`

---

### Task 3: SaleSummaryQueryRepository 추출 + 직접 테스트

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/sales/repository/SaleSummaryQueryRepository.kt`
- Modify: `src/main/kotlin/kr/ai/flori/sales/service/SaleService.kt`
- Test: `src/test/kotlin/kr/ai/flori/sales/repository/SaleSummaryQueryRepositoryTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성** — summary 집계 검증

테스트 케이스: (라벨은 LabelSettingService/Reader로 실제 생성해 payment value 매핑 검증)
1. 필터 없음 — total(미수 제외 합계)·cnt(전체)·버킷(card/cash 등) 검증
2. 월 필터 / 기간 필터 — 범위 밖 매출 제외
3. 카테고리/결제수단 IN 필터
4. search 필터 — customer_name/memo LIKE, `%`·`_` 이스케이프 케이스 포함
5. 테넌트 격리

- [ ] **Step 2: 구현** — SaleService.kt에서 그대로 이동: `SUMMARY_SELECT`(:405-416), `appendFilters`(:120-153), `appendInClause`(:155-171), `ALLOWED_SUMMARY_COLUMNS`·`SEARCH_FIELD_COUNT`·`EMPTY_SUMMARY`(:395-401), `summary()` 본문의 jdbcTemplate 호출(:99-116)

```kotlin
@Repository
class SaleSummaryQueryRepository(private val jdbcTemplate: JdbcTemplate) {
    @Suppress("LongParameterList")
    fun summarize(
        userId: Long, month: String?, startDate: String?, endDate: String?,
        categories: List<Long>?, payments: List<Long>?, channels: List<Long>?, search: String?,
    ): SalesSummaryResponse { /* SQL·빌딩 로직 그대로 */ }
}
```

- [ ] **Step 3: SaleService.summary()를 위임 1줄로 교체, jdbcTemplate 의존 제거**
- [ ] **Step 4: 테스트** — `./gradlew test --tests "kr.ai.flori.sales.*"` PASS
- [ ] **Step 5: 전체 빌드** — `./gradlew build test` PASS
- [ ] **Step 6: 커밋** — `refactor(sales): summary 네이티브 SQL을 SaleSummaryQueryRepository로 이동 + 직접 테스트`

---

### Task 4: 등급 정책을 CustomerGradeService로 통합

**Files:**
- Modify: `src/main/kotlin/kr/ai/flori/customers/service/CustomerGradeService.kt` (autoGradeId·recomputeGrade 이동 수용)
- Modify: `src/main/kotlin/kr/ai/flori/customers/service/CustomerService.kt` (등급 계산 위임)
- Modify: `src/main/kotlin/kr/ai/flori/sales/service/SaleService.kt` (recomputeGrade 호출 대상 변경)
- Test: 기존 테스트 무수정 통과 + `CustomerGradeServiceTest`에 recompute 케이스 추가(없으면 신설)

설계 결정: **등급 정책(어떤 구매횟수에 어떤 등급인가) = CustomerGradeService 소유.** 응답 조립(toResponse)은 CustomerService에 남긴다 — updateGrade/revertGradeToAuto의 컨트롤러 진입점은 CustomerService에 유지하고 내부에서 정책만 위임.

- [ ] **Step 1: `autoGradeId`(CustomerService.kt:169-177)와 `recomputeGrade`(:180-191)를 CustomerGradeService로 이동** — autoGradeId는 `fun gradeIdFor(userId: Long, purchaseCount: Int): Long?`로 공개. recomputeGrade는 CustomerQueryRepository.statsFor로 count 조회 (Task 2 산출물 의존)
- [ ] **Step 2: 호출부 교체** — CustomerService.create/findOrCreate/revertGradeToAuto → `gradeService.gradeIdFor(...)`; SaleService.create/update/delete → `gradeService.recomputeGrade(...)` (CustomerService 주입 자체가 불필요해지면 제거)
- [ ] **Step 3: 순환 의존 확인** — CustomerGradeService → CustomerQueryRepository(OK), CustomerService → CustomerGradeService(기존), SaleService → CustomerGradeService + CustomerService.findOrCreate. 순환 없음 확인
- [ ] **Step 4: recompute 단위 테스트 추가** — 임계 도달 시 승급, gradeLocked 시 불변, 매출 삭제 후 강등
- [ ] **Step 5: 전체 빌드** — `./gradlew build test` PASS
- [ ] **Step 6: 커밋** — `refactor(customers): 등급 정책을 CustomerGradeService로 통합(자동승급 규칙 단일 소유)`

---

### Task 5: SaleService 미수 전이 분리 (SaleUnpaidService + 응답 조립 공유)

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/sales/service/SaleResponseAssembler.kt` (saleLabels/toResponse/SaleLabels 이동 — SaleService.kt:328-356)
- Create: `src/main/kotlin/kr/ai/flori/sales/service/SaleUnpaidService.kt` (completeUnpaid/revertUnpaid/applyUnpaidTransition — SaleService.kt:271-310)
- Modify: `src/main/kotlin/kr/ai/flori/sales/service/SaleService.kt`, `SaleController.kt` (미수 엔드포인트 호출 대상 변경)

- [ ] **Step 1: SaleResponseAssembler 추출** — 라벨 해석 + SaleResponse 조립을 컴포넌트로. SaleService·SaleUnpaidService가 공유

```kotlin
@Component
class SaleResponseAssembler(private val labelReader: LabelSettingReader) {
    fun single(sale: Sale): SaleResponse = ...        // 기존 single()
    fun toResponse(sale: Sale, labels: SaleLabels, photos: List<String> = emptyList()): SaleResponse = ...
    fun saleLabels(): SaleLabels = ...
}
```

- [ ] **Step 2: SaleUnpaidService 추출** — load(테넌트 격리 조회)·검증·전이 로직 이동. `applyTransition(sale, request)`는 SaleService.update가 호출

```kotlin
@Service
class SaleUnpaidService(
    private val saleRepository: SaleRepository,
    private val labelReader: LabelSettingReader,
    private val assembler: SaleResponseAssembler,
) {
    @Transactional fun complete(id: Long, paymentMethodId: Long): SaleResponse { ... }
    @Transactional fun revert(id: Long): SaleResponse { ... }
    fun applyTransition(sale: Sale, request: SaleUpdateRequest) { ... }
}
```

- [ ] **Step 3: SaleController의 미수 엔드포인트를 SaleUnpaidService로 연결, SaleService에서 해당 메서드 삭제**
- [ ] **Step 4: 전체 빌드** — `./gradlew build test` PASS (Sale RestDocs 무수정 통과 = 계약 불변)
- [ ] **Step 5: 커밋** — `refactor(sales): 미수 전이를 SaleUnpaidService로 분리 + 응답 조립(SaleResponseAssembler) 공유`

---

### Task 6: 페이지네이션 표준화 (common/util/Paging)

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/common/util/Paging.kt`
- Modify: `SaleController.kt`+`SaleService.kt`, `ExpenseController.kt`+`ExpenseService.kt`, `CommunityController.kt`+`CommunityService.kt`, `CustomerService.getCustomerSales`, `AdminUserService.kt`, `AdminVerificationService.kt`
- Test: `src/test/kotlin/kr/ai/flori/common/util/PagingTest.kt` (순수 단위 테스트)

현황: 컨트롤러 3곳에 동일한 `MIN_LIMIT/MAX_LIMIT + coerce` 복붙, 서비스 3곳에 `PageRequest.of(offset / limit, limit)` 복붙, page/size 방식 3곳에 `coerceAtLeast/coerceIn` 복붙.

- [ ] **Step 1: 단위 테스트 작성** — coerce 경계(0, 음수, 상한 초과), offset→page 변환(기존 공식 `offset / limit` 동일성), sort 전달
- [ ] **Step 2: 구현**

```kotlin
/** 페이지네이션 파라미터 → Pageable 변환 SSOT. coerce 규칙·offset→page 공식을 한곳에 고정한다. */
object Paging {
    fun offsetLimit(offset: Int, limit: Int, maxLimit: Int, sort: Sort = Sort.unsorted()): Pageable {
        val safeLimit = limit.coerceIn(1, maxLimit)
        val safeOffset = offset.coerceAtLeast(0)
        return PageRequest.of(safeOffset / safeLimit, safeLimit, sort)
    }

    fun pageSize(page: Int, size: Int, maxSize: Int, sort: Sort = Sort.unsorted()): Pageable =
        PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, maxSize), sort)
}
```

- [ ] **Step 3: 호출부 교체** — 동작 동일성 주의: 컨트롤러 coerce 후 서비스에서 변환하던 2단계를 helper 1회 호출로. 각 도메인의 상한값(100/50/200)은 그대로 유지
- [ ] **Step 4: 전체 빌드** — `./gradlew build test` PASS
- [ ] **Step 5: 커밋** — `refactor(common): 페이지네이션 변환 SSOT(Paging) 도입 — coerce·offset→page 공식 통일`

---

### Task 7: SaleSpecifications 직접 테스트

**Files:**
- Test: `src/test/kotlin/kr/ai/flori/sales/repository/SaleSpecificationsTest.kt`

- [ ] **Step 1: 테스트 작성** — `SaleSpecifications.filter(...)`를 saleRepository.findAll(spec)으로 실행해 검증: 월/기간 필터, categories/payments/channels IN, search(이름·메모, 이스케이프), **테넌트 격리**(타 userId 데이터 미노출), summary(Task 3)와 동일 필터 규약 — 동일 데이터에서 list 건수 == summary cnt 인 cross-check 케이스 1개
- [ ] **Step 2: 실행** — `./gradlew test --tests "*SaleSpecificationsTest"` PASS
- [ ] **Step 3: 커밋** — `test(sales): SaleSpecifications 직접 테스트(필터 규약·테넌트 격리·summary 정합)`

---

### Task 8: 문서화 + 마무리

**Files:**
- Create: `docs/refactoring/26-06-11-service-sql-layering.md` (리팩터링 기록 — 문제→이유→개선, 사용자 가독성 우선)
- Modify: `docs/PATTERNS.md` (QueryRepository 패턴·Paging·픽스처 사용법 추가)
- Modify: `CLAUDE.md` (주요 클래스 위치 표에 신규 클래스 반영)

- [ ] **Step 1: 리팩터링 기록 작성** — 항목별로 "무엇이 문제였나 / 왜 문제인가 / 어떻게 바꿨나 / 무엇을 의도적으로 안 했나(statistics 제외 판단 포함)" + before/after 코드 스니펫
- [ ] **Step 2: PATTERNS.md·CLAUDE.md 갱신**
- [ ] **Step 3: 최종 전체 빌드** — `./gradlew build test` PASS + `./gradlew openapi3` 재생성 후 diff 없음(또는 의미 없는 diff) 확인 = 계약 불변 최종 증명
- [ ] **Step 4: 커밋** — `docs: 타겟 리팩터링 기록 + 패턴 문서 갱신`
- [ ] **Step 5: `/feature-finalize`로 PR 생성** (머지는 사용자 결정)

---

## Self-Review 체크 결과

- 스펙 커버리지: 설계 6단계 ↔ Task 1(안전망) / 2·3(SQL 이동) / 4·5(서비스 분리) / 6(페이지네이션) / 2·3·7(리포지토리 테스트) / 8(문서화) — 전부 매핑됨
- 타입 일관성: Task 4의 recomputeGrade는 Task 2의 CustomerQueryRepository.statsFor에 의존 — 순서 고정(2→4)
- 실행 중 발견 사항이 계획과 다르면(엔티티 시그니처, TenantContext API 등) 계획의 의도를 유지한 채 실제 코드에 맞춘다. 동작 변경이 의심되는 순간 중단하고 보고.
