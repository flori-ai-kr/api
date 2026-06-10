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
import kr.ai.flori.customers.repository.CustomerRepository
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
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val saleRepository: SaleRepository,
    private val labelReader: LabelSettingReader,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun list(): List<CustomerResponse> {
        val userId = TenantContext.currentUserId()
        val statsByCustomer = aggregateStats(userId)
        return customerRepository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .map { CustomerResponse.from(it, statsByCustomer[it.id] ?: CustomerStats.EMPTY) }
            .sortedByDescending { it.totalPurchaseAmount }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): CustomerResponse {
        val customer = load(id)
        return CustomerResponse.from(customer, statsFor(customer.userId, id))
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
        return customerRepository
            .findTop10ByUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(TenantContext.currentUserId(), query)
            .map { CustomerSearchResult(requireNotNull(it.id), it.name, it.phone, it.grade) }
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
        return found?.let { CustomerSearchResult(requireNotNull(it.id), it.name, it.phone, it.grade) }
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
        val customer = Customer(userId, requireNotNull(request.name), phone)
        customer.grade = validGrade(request.grade ?: DEFAULT_GRADE)
        customer.gender = validGender(request.gender)
        customer.memo = request.memo
        return CustomerResponse.from(customerRepository.save(customer), CustomerStats.EMPTY)
    }

    @Transactional
    fun update(
        id: Long,
        request: CustomerUpdateRequest,
    ): CustomerResponse {
        val customer = load(id)
        request.name?.let { customer.name = it }
        request.phone?.let { customer.phone = it }
        request.grade?.let { customer.grade = validGrade(it) }
        request.gender?.let { customer.gender = validGender(it) }
        request.memo?.let { customer.memo = it }
        return CustomerResponse.from(saveUnique(customer), statsFor(customer.userId, id))
    }

    @Transactional
    fun updateGrade(
        id: Long,
        grade: String,
    ): CustomerResponse {
        val customer = load(id)
        customer.grade = validGrade(grade)
        return CustomerResponse.from(customerRepository.save(customer), statsFor(customer.userId, id))
    }

    @Transactional
    fun delete(id: Long) {
        val customer = load(id)
        // FK 미사용: 이 고객을 참조하던 매출의 customer_id를 직접 NULL 처리(매출 기록은 보존).
        saleRepository.clearCustomerReference(customer.userId, id)
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
            return CustomerResponse.from(it, statsFor(userId, requireNotNull(it.id)))
        }
        return try {
            CustomerResponse.from(customerRepository.save(Customer(userId, name, phone)), CustomerStats.EMPTY)
        } catch (_: DataIntegrityViolationException) {
            // 동시 생성 레이스: 다른 트랜잭션이 먼저 생성 → 재조회
            val existing = requireNotNull(customerRepository.findByUserIdAndPhone(userId, phone))
            CustomerResponse.from(existing, statsFor(userId, requireNotNull(existing.id)))
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

    private fun validGrade(grade: String): String {
        if (grade !in GRADES) throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 등급입니다")
        return grade
    }

    private fun validGender(gender: String?): String? {
        if (gender == null) return null
        if (gender !in GENDERS) throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 성별입니다")
        return gender
    }

    private companion object {
        const val DEFAULT_GRADE = "new"
        const val MAX_PAGE_SIZE = 50
        val GRADES = setOf("new", "regular", "vip", "blacklist")
        val GENDERS = setOf("male", "female")
    }
}
