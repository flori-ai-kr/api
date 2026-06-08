package kr.ai.flori.customers.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerCreateRequest
import kr.ai.flori.customers.dto.CustomerResponse
import kr.ai.flori.customers.dto.CustomerSearchResult
import kr.ai.flori.customers.dto.CustomerStats
import kr.ai.flori.customers.dto.CustomerUpdateRequest
import kr.ai.flori.customers.entity.Customer
import kr.ai.flori.customers.repository.CustomerGradeRepository
import kr.ai.flori.customers.repository.CustomerRepository
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.sales.dto.SaleResponse
import kr.ai.flori.sales.dto.SalesPageResponse
import kr.ai.flori.sales.repository.SaleRepository
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 고객 서비스. 모든 쿼리 TenantContext userId 격리(HARD).
 * 구매 통계는 sales에서 실시간 집계(네이티브 SQL, SSOT).
 */
@Service
@Suppress("TooManyFunctions")
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val gradeRepository: CustomerGradeRepository,
    private val gradeService: CustomerGradeService,
    private val saleRepository: SaleRepository,
    private val photoCardRepository: PhotoCardRepository,
    private val labelReader: LabelSettingReader,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun list(): List<CustomerResponse> {
        val userId = TenantContext.currentUserId()
        val statsByCustomer = aggregateStats(userId)
        val gradeNames = gradeNamesById(userId)
        val photoSummary = photoSummaryByCustomer(userId)
        return customerRepository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .map { c ->
                val (thumbs, count) = photoSummary[c.id] ?: (emptyList<String>() to 0)
                CustomerResponse.from(
                    c,
                    statsByCustomer[c.id] ?: CustomerStats.EMPTY,
                    c.gradeId?.let { gradeNames[it] },
                    thumbs,
                    count,
                )
            }.sortedByDescending { it.totalPurchaseAmount }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): CustomerResponse {
        val customer = load(id)
        return toResponse(customer)
    }

    /** 고객별 구매(매출) 건수 일괄 조회. 예약 카드의 'N번 방문' 배지 등 enrichment 용도. */
    @Transactional(readOnly = true)
    fun purchaseCountsByCustomer(): Map<Long, Int> =
        jdbcTemplate
            .query(
                "SELECT customer_id, count(*) AS cnt FROM sales " +
                    "WHERE user_id = ?::bigint AND customer_id IS NOT NULL GROUP BY customer_id",
                { rs, _ -> rs.getLong("customer_id") to rs.getInt("cnt") },
                TenantContext.currentUserId(),
            ).toMap()

    @Transactional(readOnly = true)
    fun searchByName(query: String): List<CustomerSearchResult> {
        if (query.isBlank()) return emptyList()
        val userId = TenantContext.currentUserId()
        val gradeNames = gradeNamesById(userId)
        return customerRepository
            .findTop10ByUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(userId, query)
            .map { CustomerSearchResult(requireNotNull(it.id), it.name, it.phone, it.gradeId?.let(gradeNames::get)) }
    }

    @Transactional(readOnly = true)
    fun checkPhoneDuplicate(
        phone: String,
        excludeId: Long?,
    ): CustomerSearchResult? {
        val userId = TenantContext.currentUserId()
        val found =
            if (excludeId != null) {
                customerRepository.findFirstByUserIdAndPhoneAndIdNot(userId, phone, excludeId)
            } else {
                customerRepository.findFirstByUserIdAndPhone(userId, phone)
            }
        return found?.let {
            CustomerSearchResult(
                requireNotNull(it.id),
                it.name,
                it.phone,
                it.gradeId?.let { gid -> gradeRepository.findByIdAndUserId(gid, userId)?.name },
            )
        }
    }

    @Transactional(readOnly = true)
    fun getCustomerSales(
        customerId: Long,
        page: Int,
        size: Int,
    ): SalesPageResponse {
        val userId = TenantContext.currentUserId()
        load(customerId) // 소유권 확인
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE), Sort.by(Sort.Order.desc("date")))
        val result = saleRepository.findByUserIdAndCustomerId(userId, customerId, pageable)
        val catMap = labelReader.labelMap(LabelDomains.SALE, LabelKinds.CATEGORY)
        val payMap = labelReader.labelMap(LabelDomains.SALE, LabelKinds.PAYMENT)
        val chMap = labelReader.labelMap(LabelDomains.SALE, LabelKinds.CHANNEL)
        return SalesPageResponse(
            result.content.map { sale ->
                SaleResponse.from(
                    sale,
                    sale.categoryId?.let { catMap[it] },
                    sale.paymentMethodId?.let { payMap[it] },
                    sale.channelId?.let { chMap[it] },
                )
            },
            result.hasNext(),
        )
    }

    @Transactional
    fun create(request: CustomerCreateRequest): CustomerResponse {
        val userId = TenantContext.currentUserId()
        val phone = requireNotNull(request.phone)
        if (customerRepository.findByUserIdAndPhone(userId, phone) != null) {
            throw AppException(CommonErrorCode.CONFLICT, "이미 등록된 전화번호입니다")
        }
        gradeService.ensureDefaults(userId)
        val customer = Customer(userId, requireNotNull(request.name), phone)
        customer.gradeId = autoGradeId(userId, 0)
        customer.gradeLocked = false
        customer.gender = validGender(request.gender)
        customer.memo = request.memo
        return toResponse(customerRepository.save(customer))
    }

    @Transactional
    fun update(
        id: Long,
        request: CustomerUpdateRequest,
    ): CustomerResponse {
        val customer = load(id)
        request.name?.let { customer.name = it }
        request.phone?.let { customer.phone = it }
        request.gender?.let { customer.gender = validGender(it) }
        request.memo?.let { customer.memo = it }
        return toResponse(saveUnique(customer))
    }

    /** 구매횟수 기준 적정 등급 id. threshold 있는 등급 중 threshold<=count 최대, 없으면 최저 sort_order. */
    private fun autoGradeId(
        userId: Long,
        count: Int,
    ): Long? {
        val grades = gradeRepository.findByUserIdOrderBySortOrderAsc(userId)
        if (grades.isEmpty()) return null
        val eligible = grades.filter { it.threshold != null && it.threshold!! <= count }.maxByOrNull { it.threshold!! }
        return (eligible ?: grades.minByOrNull { it.sortOrder })?.id
    }

    /** 자동 등급 재계산(잠금 아니면). 매출 변경/되돌리기 후 호출. */
    @Transactional
    fun recomputeGrade(customerId: Long) {
        val userId = TenantContext.currentUserId()
        val customer = customerRepository.findByIdAndUserId(customerId, userId) ?: return
        if (customer.gradeLocked) return
        val count = statsFor(userId, customerId).count
        val newGradeId = autoGradeId(userId, count)
        if (newGradeId != null && newGradeId != customer.gradeId) {
            customer.gradeId = newGradeId
            customerRepository.save(customer)
        }
    }

    /** 수동 등급 지정 → 잠금. */
    @Transactional
    fun updateGrade(
        id: Long,
        gradeId: Long,
    ): CustomerResponse {
        val customer = load(id)
        gradeRepository.findByIdAndUserId(gradeId, customer.userId)
            ?: throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 등급입니다")
        customer.gradeId = gradeId
        customer.gradeLocked = true
        return toResponse(customerRepository.save(customer))
    }

    /** 자동 등급으로 되돌리기 → 잠금 해제 후 재계산. */
    @Transactional
    fun revertGradeToAuto(id: Long): CustomerResponse {
        val customer = load(id)
        customer.gradeLocked = false
        customer.gradeId = autoGradeId(customer.userId, statsFor(customer.userId, id).count)
        return toResponse(customerRepository.save(customer))
    }

    @Transactional
    fun delete(id: Long) {
        val customer = load(id)
        // FK 미사용: 이 고객을 참조하던 매출의 customer_id를 직접 NULL 처리(매출 기록은 보존).
        saleRepository.clearCustomerReference(customer.userId, id)
        // 사진 카드의 customer_id도 동일하게 NULL 처리(카드는 보존).
        photoCardRepository.clearCustomerReference(customer.userId, id)
        customerRepository.delete(customer)
    }

    /** 전화번호+user_id 복합 unique 기반 찾기/생성(매출 등록 시 연결용). */
    @Transactional
    fun findOrCreate(
        name: String,
        phone: String,
    ): CustomerResponse {
        val userId = TenantContext.currentUserId()
        customerRepository.findByUserIdAndPhone(userId, phone)?.let {
            return toResponse(it)
        }
        gradeService.ensureDefaults(userId)
        return try {
            val customer =
                Customer(userId, name, phone).apply {
                    gradeId = autoGradeId(userId, 0)
                    gradeLocked = false
                }
            toResponse(customerRepository.save(customer))
        } catch (_: DataIntegrityViolationException) {
            // 동시 생성 레이스: 다른 트랜잭션이 먼저 생성 → 재조회
            val existing = requireNotNull(customerRepository.findByUserIdAndPhone(userId, phone))
            toResponse(existing)
        }
    }

    private fun saveUnique(customer: Customer): Customer =
        try {
            customerRepository.save(customer)
        } catch (_: DataIntegrityViolationException) {
            throw AppException(CommonErrorCode.CONFLICT, "이미 등록된 전화번호입니다")
        }

    private fun load(id: Long): Customer =
        customerRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "고객을 찾을 수 없습니다")

    /** 단건 응답: 등급명·통계·대표사진(썸네일3·카운트)을 해석해 조립. */
    private fun toResponse(customer: Customer): CustomerResponse {
        val id = requireNotNull(customer.id)
        val gradeName = customer.gradeId?.let { gradeRepository.findByIdAndUserId(it, customer.userId)?.name }
        val (thumbs, count) = photoSummaryFor(customer.userId, id)
        return CustomerResponse.from(customer, statsFor(customer.userId, id), gradeName, thumbs, count)
    }

    /** gradeId → 등급명 맵(테넌트 1쿼리). */
    private fun gradeNamesById(userId: Long): Map<Long, String> =
        gradeRepository
            .findByUserIdOrderBySortOrderAsc(userId)
            .associate { requireNotNull(it.id) to it.name }

    /**
     * 고객별 대표 썸네일 3장 + 카운트 (1쿼리). photos jsonb 의 첫 요소 url.
     * photos jsonb 형태: [{"url":...,"originalName":...}] — photos->0->>'url' 이 대표 썸네일.
     */
    private fun photoSummaryByCustomer(userId: Long): Map<Long, Pair<List<String>, Int>> =
        jdbcTemplate
            .query(
                """
                SELECT customer_id,
                       count(*) AS cnt,
                       (array_agg((photos->0->>'url') ORDER BY created_at DESC)
                          FILTER (WHERE jsonb_array_length(photos) > 0))[1:3] AS thumbs
                FROM photo_cards
                WHERE user_id = ?::bigint AND customer_id IS NOT NULL
                GROUP BY customer_id
                """.trimIndent(),
                { rs, _ ->
                    val arr = rs.getArray("thumbs")?.array as? Array<*>
                    val thumbs = arr?.filterIsInstance<String>() ?: emptyList()
                    rs.getLong("customer_id") to (thumbs to rs.getInt("cnt"))
                },
                userId,
            ).toMap()

    /** 단일 고객 대표 썸네일 3장 + 카운트. */
    private fun photoSummaryFor(
        userId: Long,
        customerId: Long,
    ): Pair<List<String>, Int> =
        jdbcTemplate
            .query(
                """
                SELECT count(*) AS cnt,
                       (array_agg((photos->0->>'url') ORDER BY created_at DESC)
                          FILTER (WHERE jsonb_array_length(photos) > 0))[1:3] AS thumbs
                FROM photo_cards
                WHERE user_id = ?::bigint AND customer_id = ?::bigint
                """.trimIndent(),
                { rs, _ ->
                    val arr = rs.getArray("thumbs")?.array as? Array<*>
                    val thumbs = arr?.filterIsInstance<String>() ?: emptyList()
                    thumbs to rs.getInt("cnt")
                },
                userId,
                customerId,
            ).firstOrNull() ?: (emptyList<String>() to 0)

    private fun statsFor(
        userId: Long,
        customerId: Long,
    ): CustomerStats =
        jdbcTemplate
            .query(
                "SELECT count(*) AS cnt, COALESCE(SUM(amount), 0) AS total, MIN(date) AS first_date, MAX(date) AS last_date " +
                    "FROM sales WHERE user_id = ?::bigint AND customer_id = ?::bigint",
                { rs, _ ->
                    CustomerStats(
                        rs.getInt("cnt"),
                        rs.getLong("total"),
                        rs.getDate("first_date")?.toLocalDate(),
                        rs.getDate("last_date")?.toLocalDate(),
                    )
                },
                userId,
                customerId,
            ).firstOrNull() ?: CustomerStats.EMPTY

    private fun aggregateStats(userId: Long): Map<Long, CustomerStats> =
        jdbcTemplate
            .query(
                "SELECT customer_id, count(*) AS cnt, COALESCE(SUM(amount), 0) AS total, " +
                    "MIN(date) AS first_date, MAX(date) AS last_date " +
                    "FROM sales WHERE user_id = ?::bigint AND customer_id IS NOT NULL GROUP BY customer_id",
                { rs, _ ->
                    rs.getLong("customer_id") to
                        CustomerStats(
                            rs.getInt("cnt"),
                            rs.getLong("total"),
                            rs.getDate("first_date")?.toLocalDate(),
                            rs.getDate("last_date")?.toLocalDate(),
                        )
                },
                userId,
            ).toMap()

    private fun validGender(gender: String?): String? {
        if (gender == null) return null
        if (gender !in GENDERS) throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 성별입니다")
        return gender
    }

    private companion object {
        const val MAX_PAGE_SIZE = 50
        val GENDERS = setOf("male", "female")
    }
}
