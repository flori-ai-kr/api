package kr.ai.flori.sales.service

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
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

/**
 * 매출 서비스. 모든 조회/변경은 TenantContext의 userId로 격리(멀티테넌시 HARD).
 * 카테고리/결제수단/채널은 label_settings.id 간접참조 — 쓰기 시 소유권 검증, 응답 시 id→label 해석.
 * 미수는 payment_method_id=NULL + is_unpaid=true 로 표현한다(결제수단 값 'unpaid' 폐지).
 */
@Service
class SaleService(
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository,
    private val reservationRepository: ReservationRepository,
    private val photoCardRepository: PhotoCardRepository,
    private val labelReader: LabelSettingReader,
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
        categories: List<Long>?,
        payments: List<Long>?,
        channels: List<Long>?,
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

        val labels = saleLabels()
        return SalesPageResponse(
            page.content.map { sale -> toResponse(sale, labels, photosBySaleId[sale.id] ?: emptyList()) },
            page.hasNext(),
        )
    }

    /**
     * 매출 요약(페이지네이션 무관 전체 합산). GET /sales 와 동일한 필터 규약으로 DB 집계(SUM/FILTER).
     * total/count는 전체(미수 포함), 명세 버킷(card/naverpay/transfer/cash)은 결제수단 라벨 value 기준.
     */
    @Suppress("LongParameterList")
    @Transactional(readOnly = true)
    fun summary(
        month: String?,
        startDate: String?,
        endDate: String?,
        categories: List<Long>?,
        payments: List<Long>?,
        channels: List<Long>?,
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
        categories: List<Long>?,
        payments: List<Long>?,
        channels: List<Long>?,
        search: String?,
    ) {
        if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
            sql.append(" AND s.date BETWEEN ? AND ?")
            params.add(Date.valueOf(LocalDate.parse(startDate)))
            params.add(Date.valueOf(LocalDate.parse(endDate)))
        } else {
            monthRange(month)?.let { (start, end) ->
                sql.append(" AND s.date BETWEEN ? AND ?")
                params.add(Date.valueOf(start))
                params.add(Date.valueOf(end))
            }
        }
        appendInClause(sql, params, "s.category_id", categories)
        appendInClause(sql, params, "s.payment_method_id", payments)
        appendInClause(sql, params, "s.channel_id", channels)
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase().replace("%", "\\%").replace("_", "\\_")}%"
            sql.append(
                " AND (lower(s.customer_name) LIKE ? OR lower(s.memo) LIKE ?)",
            )
            repeat(SEARCH_FIELD_COUNT) { params.add(pattern) }
        }
    }

    private fun appendInClause(
        sql: StringBuilder,
        params: MutableList<Any>,
        column: String,
        values: List<*>?,
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
        values.forEach { params.add(it as Any) }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): SaleResponse = single(load(id))

    @Transactional(readOnly = true)
    fun suggestions(): List<String> = saleRepository.findMemosByFrequency(TenantContext.currentUserId())

    @Transactional
    fun create(request: SaleCreateRequest): SaleResponse {
        val userId = TenantContext.currentUserId()
        val categoryId = labelReader.requireOwned(requireNotNull(request.categoryId), LabelDomains.SALE, LabelKinds.CATEGORY)
        val date = requireNotNull(request.date)
        val amount = requireNotNull(request.amount)
        request.customerId?.let { verifyCustomerOwnership(userId, it) }

        // 미수면 결제수단 미지정(NULL), 아니면 결제수단 id 필수·소유권 검증.
        val paymentMethodId =
            if (request.isUnpaid) {
                null
            } else {
                labelReader.requireOwned(requirePaymentId(request.paymentMethodId), LabelDomains.SALE, LabelKinds.PAYMENT)
            }
        val sale =
            Sale(
                userId = userId,
                date = date,
                categoryId = categoryId,
                amount = amount,
                paymentMethodId = paymentMethodId,
            )
        sale.channelId =
            request.channelId?.let { labelReader.requireOwned(it, LabelDomains.SALE, LabelKinds.CHANNEL) }
                ?: labelReader.defaultSaleChannelId()
        sale.isUnpaid = request.isUnpaid
        sale.customerName = request.customerName
        sale.customerPhone = request.customerPhone
        sale.customerId = request.customerId
        sale.memo = request.memo
        return single(saleRepository.save(sale))
    }

    @Transactional
    fun update(
        id: Long,
        request: SaleUpdateRequest,
    ): SaleResponse {
        val userId = TenantContext.currentUserId()
        val sale = load(id)
        request.paymentMethodId?.let { sale.paymentMethodId = labelReader.requireOwned(it, LabelDomains.SALE, LabelKinds.PAYMENT) }
        request.date?.let { sale.date = it }
        request.amount?.let { sale.amount = it }
        request.categoryId?.let { sale.categoryId = labelReader.requireOwned(it, LabelDomains.SALE, LabelKinds.CATEGORY) }
        request.channelId?.let { sale.channelId = labelReader.requireOwned(it, LabelDomains.SALE, LabelKinds.CHANNEL) }
        request.customerName?.let { sale.customerName = it }
        request.customerPhone?.let { sale.customerPhone = it }
        request.customerId?.let {
            verifyCustomerOwnership(userId, it)
            sale.customerId = it
        }
        request.memo?.let { sale.memo = it }
        request.hasReview?.let { sale.hasReview = it }
        // is_unpaid 마커는 수정에서 변경하지 않음(생성 시 결정, complete/revert로만 전이)
        return single(saleRepository.save(sale))
    }

    @Transactional
    fun completeUnpaid(
        id: Long,
        paymentMethodId: Long,
    ): SaleResponse {
        val sale = load(id)
        if (!sale.isUnpaid) throw AppException(CommonErrorCode.VALIDATION, "미수 매출이 아닙니다")
        // 미수 완료: 실제 결제수단 확정(미수 마커 is_unpaid는 유지 — '미수였던 건' 추적용)
        sale.paymentMethodId = labelReader.requireOwned(paymentMethodId, LabelDomains.SALE, LabelKinds.PAYMENT)
        return single(saleRepository.save(sale))
    }

    @Transactional
    fun revertUnpaid(id: Long): SaleResponse {
        val sale = load(id)
        if (!sale.isUnpaid) throw AppException(CommonErrorCode.VALIDATION, "미수 매출이 아닙니다")
        sale.paymentMethodId = null
        return single(saleRepository.save(sale))
    }

    @Transactional
    fun delete(id: Long) {
        val sale = load(id)
        // FK 미사용: 이 매출을 참조하던 예약·사진 카드의 sale_id를 직접 NULL 처리(둘 다 보존).
        reservationRepository.clearSaleReference(sale.userId, id)
        photoCardRepository.clearSaleReference(sale.userId, id)
        saleRepository.delete(sale)
    }

    /** 단건 응답 — 현재 테넌트의 카테고리/결제수단/채널 라벨을 해석해 채운다. */
    private fun single(sale: Sale): SaleResponse = toResponse(sale, saleLabels())

    private fun toResponse(
        sale: Sale,
        labels: SaleLabels,
        photos: List<String> = emptyList(),
    ): SaleResponse =
        SaleResponse.from(
            sale,
            sale.categoryId?.let { labels.categories[it] },
            sale.paymentMethodId?.let { labels.payments[it] },
            sale.channelId?.let { labels.channels[it] },
            photos,
        )

    /** 현재 테넌트의 매출 라벨(카테고리·결제수단·채널) id→label 맵 묶음. */
    private fun saleLabels(): SaleLabels =
        SaleLabels(
            labelReader.labelMap(LabelDomains.SALE, LabelKinds.CATEGORY),
            labelReader.labelMap(LabelDomains.SALE, LabelKinds.PAYMENT),
            labelReader.labelMap(LabelDomains.SALE, LabelKinds.CHANNEL),
        )

    private data class SaleLabels(
        val categories: Map<Long, String>,
        val payments: Map<Long, String>,
        val channels: Map<Long, String>,
    )

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

    private fun requirePaymentId(id: Long?): Long = id ?: throw AppException(CommonErrorCode.VALIDATION, "결제방식은 필수입니다(미수가 아니면 결제수단을 지정하세요)")

    private companion object {
        /** summary 검색 패턴이 적용되는 컬럼 수(customer_name·memo). */
        const val SEARCH_FIELD_COUNT = 2

        /** appendInClause가 SQL에 직접 끼워 넣어도 안전한 컬럼 화이트리스트(식별자 주입 방어). */
        val ALLOWED_SUMMARY_COLUMNS = setOf("s.category_id", "s.payment_method_id", "s.channel_id")
        val EMPTY_SUMMARY = SalesSummaryResponse(0, 0, 0, 0, 0, 0)

        // 고정 버킷(card/naverpay/transfer/cash)은 결제수단 라벨의 value 로 매핑(label_settings JOIN).
        // total/cnt 는 전체(미수 포함), 버킷은 해당 value 만 합산.
        val SUMMARY_SELECT =
            """
            SELECT
              COALESCE(SUM(s.amount), 0) AS total,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'card'), 0) AS card,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'naverpay'), 0) AS naverpay,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'transfer'), 0) AS transfer,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'cash'), 0) AS cash,
              COUNT(*) AS cnt
            FROM sales s LEFT JOIN label_settings ls ON ls.id = s.payment_method_id
            WHERE s.user_id = ?::bigint
            """.trimIndent()
    }
}
