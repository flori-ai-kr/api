package kr.ai.flori.sales.service

import kr.ai.flori.common.domain.PaymentMethods
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.monthRange
import kr.ai.flori.customers.repository.CustomerRepository
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.reservations.repository.ReservationRepository
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.dto.SaleResponse
import kr.ai.flori.sales.dto.SaleUpdateRequest
import kr.ai.flori.sales.dto.SalesPageResponse
import kr.ai.flori.sales.dto.SalesSummaryResponse
import kr.ai.flori.sales.entity.Sale
import kr.ai.flori.sales.repository.SaleRepository
import kr.ai.flori.sales.repository.SaleSpecifications
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

/**
 * 매출 서비스. 모든 조회/변경은 TenantContext의 userId로 격리(멀티테넌시 HARD).
 */
@Service
class SaleService(
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository,
    private val reservationRepository: ReservationRepository,
    private val photoCardRepository: PhotoCardRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Suppress("LongParameterList")
    @Transactional(readOnly = true)
    fun list(
        month: String?,
        startDate: String?,
        endDate: String?,
        offset: Int,
        limit: Int,
        categories: List<String>?,
        payments: List<String>?,
        channels: List<String>?,
        search: String?,
    ): SalesPageResponse {
        val userId = TenantContext.currentUserId()
        val spec = SaleSpecifications.filter(userId, month, startDate, endDate, categories, payments, channels, search)
        val sort = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt"))
        val pageable = PageRequest.of(offset / limit, limit, sort)
        val page = saleRepository.findAll(spec, pageable)

        // 목록 썸네일 표시용: 현재 페이지 매출들의 사진을 한 번에 조회해 saleId -> URL 목록으로 매핑
        val saleIds = page.content.mapNotNull { it.id }
        val photosBySaleId =
            if (saleIds.isEmpty()) {
                emptyMap()
            } else {
                photoCardRepository
                    .findByUserIdAndSaleIdIn(userId, saleIds)
                    .filter { it.saleId != null }
                    .associate { card -> card.saleId!! to card.photos.map { it.url } }
            }

        return SalesPageResponse(
            page.content.map { sale -> SaleResponse.from(sale, photosBySaleId[sale.id] ?: emptyList()) },
            page.hasNext(),
        )
    }

    /**
     * 매출 요약(페이지네이션 무관 전체 합산). GET /sales 와 동일한 필터 규약으로 DB 집계(SUM/FILTER).
     * total/count는 전체(미수·kakaopay 포함), 명세 버킷(card/naverpay/transfer/cash)은 해당 결제수단만.
     *
     * 전체 행 로드 후 인메모리 합산 대신 DashboardService와 동일한 네이티브 SQL 집계로 처리한다
     * (month=null 누적 조회 시에도 전체 매출을 메모리로 끌어오지 않는다).
     */
    @Transactional(readOnly = true)
    fun summary(
        month: String?,
        startDate: String?,
        endDate: String?,
        categories: List<String>?,
        payments: List<String>?,
        channels: List<String>?,
        search: String?,
    ): SalesSummaryResponse {
        val userId = TenantContext.currentUserId()
        val sql = StringBuilder(SUMMARY_SELECT)
        val params = mutableListOf<Any>(userId)
        appendFilters(sql, params, month, startDate, endDate, categories, payments, channels, search)
        return jdbcTemplate.queryForObject(
            sql.toString(),
            { rs, _ ->
                SalesSummaryResponse(
                    rs.getLong("total"),
                    rs.getLong("card"),
                    rs.getLong("naverpay"),
                    rs.getLong("transfer"),
                    rs.getLong("cash"),
                    rs.getLong("cnt"),
                )
            },
            *params.toTypedArray(),
        ) ?: EMPTY_SUMMARY
    }

    /** summary 의 동적 WHERE 절을 SaleSpecifications.filter 와 동일한 규약으로 구성한다. */
    @Suppress("LongParameterList")
    private fun appendFilters(
        sql: StringBuilder,
        params: MutableList<Any>,
        month: String?,
        startDate: String?,
        endDate: String?,
        categories: List<String>?,
        payments: List<String>?,
        channels: List<String>?,
        search: String?,
    ) {
        if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
            sql.append(" AND date BETWEEN ? AND ?")
            params.add(Date.valueOf(LocalDate.parse(startDate)))
            params.add(Date.valueOf(LocalDate.parse(endDate)))
        } else {
            monthRange(month)?.let { (start, end) ->
                sql.append(" AND date BETWEEN ? AND ?")
                params.add(Date.valueOf(start))
                params.add(Date.valueOf(end))
            }
        }
        appendInClause(sql, params, "product_category", categories)
        appendInClause(sql, params, "payment_method", payments)
        appendInClause(sql, params, "reservation_channel", channels)
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase().replace("%", "\\%").replace("_", "\\_")}%"
            sql.append(
                " AND (lower(customer_name) LIKE ? OR lower(memo) LIKE ?)",
            )
            repeat(SEARCH_FIELD_COUNT) { params.add(pattern) }
        }
    }

    private fun appendInClause(
        sql: StringBuilder,
        params: MutableList<Any>,
        column: String,
        values: List<String>?,
    ) {
        // column은 호출부의 컴파일타임 상수만 허용(식별자는 바인딩 불가) — 미래의 사용자 입력 주입을 원천 차단.
        require(column in ALLOWED_SUMMARY_COLUMNS) { "허용되지 않은 집계 컬럼: $column" }
        if (values.isNullOrEmpty()) return
        sql
            .append(" AND ")
            .append(column)
            .append(" IN (")
            .append(values.joinToString(",") { "?" })
            .append(")")
        params.addAll(values)
    }

    @Transactional(readOnly = true)
    fun get(id: Long): SaleResponse = SaleResponse.from(load(id))

    @Transactional(readOnly = true)
    fun suggestions(): List<String> = saleRepository.findMemosByFrequency(TenantContext.currentUserId())

    @Transactional
    fun create(request: SaleCreateRequest): SaleResponse {
        val userId = TenantContext.currentUserId()
        val paymentMethod = requireValidPaymentMethod(requireNotNull(request.paymentMethod), allowUnpaid = true)
        val category = requireNotNull(request.productCategory)
        val date = requireNotNull(request.date)
        val amount = requireNotNull(request.amount)
        request.customerId?.let { verifyCustomerOwnership(userId, it) }

        val isUnpaid = paymentMethod == PaymentMethods.UNPAID
        val sale =
            Sale(
                userId = userId,
                date = date,
                productCategory = category,
                amount = amount,
                paymentMethod = paymentMethod,
            )
        sale.reservationChannel = request.reservationChannel ?: DEFAULT_CHANNEL
        sale.isUnpaid = isUnpaid
        sale.customerName = request.customerName
        sale.customerPhone = request.customerPhone
        sale.customerId = request.customerId
        sale.memo = request.memo
        return SaleResponse.from(saleRepository.save(sale))
    }

    @Transactional
    fun update(
        id: Long,
        request: SaleUpdateRequest,
    ): SaleResponse {
        val userId = TenantContext.currentUserId()
        val sale = load(id)
        request.paymentMethod?.let { sale.paymentMethod = requireValidPaymentMethod(it, allowUnpaid = true) }
        request.date?.let { sale.date = it }
        request.amount?.let { sale.amount = it }
        request.productCategory?.let { sale.productCategory = it }
        request.reservationChannel?.let { sale.reservationChannel = it }
        request.customerName?.let { sale.customerName = it }
        request.customerPhone?.let { sale.customerPhone = it }
        request.customerId?.let {
            verifyCustomerOwnership(userId, it)
            sale.customerId = it
        }
        request.memo?.let { sale.memo = it }
        request.hasReview?.let { sale.hasReview = it }
        // is_unpaid 마커는 수정에서 변경하지 않음(생성 시 결정, complete/revert로만 전이)
        return SaleResponse.from(saleRepository.save(sale))
    }

    @Transactional
    fun completeUnpaid(
        id: Long,
        paymentMethod: String,
    ): SaleResponse {
        val sale = load(id)
        if (!sale.isUnpaid) throw AppException(CommonErrorCode.VALIDATION, "미수 매출이 아닙니다")
        sale.paymentMethod = requireValidPaymentMethod(paymentMethod, allowUnpaid = false)
        return SaleResponse.from(saleRepository.save(sale))
    }

    @Transactional
    fun revertUnpaid(id: Long): SaleResponse {
        val sale = load(id)
        if (!sale.isUnpaid) throw AppException(CommonErrorCode.VALIDATION, "미수 매출이 아닙니다")
        sale.paymentMethod = PaymentMethods.UNPAID
        return SaleResponse.from(saleRepository.save(sale))
    }

    @Transactional
    fun delete(id: Long) {
        val sale = load(id)
        // FK 미사용: 이 매출을 참조하던 예약·사진 카드의 sale_id를 직접 NULL 처리(둘 다 보존).
        reservationRepository.clearSaleReference(sale.userId, id)
        photoCardRepository.clearSaleReference(sale.userId, id)
        saleRepository.delete(sale)
    }

    private fun load(id: Long): Sale =
        saleRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "매출을 찾을 수 없습니다")

    // 고객 도메인 리포지토리를 통해 소유권 검증(raw SQL 대신 도메인 경계 준수)
    private fun verifyCustomerOwnership(
        userId: Long,
        customerId: Long,
    ) {
        if (customerRepository.findByIdAndUserId(customerId, userId) == null) {
            throw AppException(CommonErrorCode.VALIDATION, "유효하지 않은 고객입니다")
        }
    }

    private fun requireValidPaymentMethod(
        value: String,
        allowUnpaid: Boolean,
    ): String {
        val allowed = if (allowUnpaid) PaymentMethods.SALE else PaymentMethods.SALE - PaymentMethods.UNPAID
        if (value !in allowed) throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 결제방식입니다")
        return value
    }

    private companion object {
        const val DEFAULT_CHANNEL = "other"

        /** summary 검색 패턴이 적용되는 컬럼 수(customer_name·memo). */
        const val SEARCH_FIELD_COUNT = 2

        /** appendInClause가 SQL에 직접 끼워 넣어도 안전한 컬럼 화이트리스트(식별자 주입 방어). */
        val ALLOWED_SUMMARY_COLUMNS = setOf("product_category", "payment_method", "reservation_channel")
        val EMPTY_SUMMARY = SalesSummaryResponse(0, 0, 0, 0, 0, 0)
        val SUMMARY_SELECT =
            """
            SELECT
              COALESCE(SUM(amount), 0) AS total,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'card'), 0) AS card,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'naverpay'), 0) AS naverpay,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'transfer'), 0) AS transfer,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'cash'), 0) AS cash,
              COUNT(*) AS cnt
            FROM sales WHERE user_id = ?::bigint
            """.trimIndent()
    }
}
