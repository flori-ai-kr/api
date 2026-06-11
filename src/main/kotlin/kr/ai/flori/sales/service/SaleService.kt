package kr.ai.flori.sales.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.service.CustomerGradeService
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
import kr.ai.flori.sales.repository.SaleSummaryQueryRepository
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 매출 서비스. 모든 조회/변경은 TenantContext의 userId로 격리(멀티테넌시 HARD).
 * 카테고리/결제수단/채널은 label_settings.id 간접참조 — 쓰기 시 소유권 검증, 응답 시 id→label 해석.
 * 미수는 payment_method_id=NULL + is_unpaid=true 로 표현한다(결제수단 값 'unpaid' 폐지).
 */
@Service
@Suppress("TooManyFunctions")
class SaleService(
    private val saleRepository: SaleRepository,
    private val customerLinker: SaleCustomerLinker,
    private val gradeService: CustomerGradeService,
    private val reservationRepository: ReservationRepository,
    private val photoCardRepository: PhotoCardRepository,
    private val labelReader: LabelSettingReader,
    private val summaryRepository: SaleSummaryQueryRepository,
    private val unpaidService: SaleUnpaidService,
    private val assembler: SaleResponseAssembler,
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

        val labels = assembler.labels()
        return SalesPageResponse(
            page.content.map { sale -> assembler.toResponse(sale, labels, photosBySaleId[sale.id] ?: emptyList()) },
            page.hasNext(),
        )
    }

    /** 매출 요약(페이지네이션 무관 전체 합산). 필터 규약·집계는 SaleSummaryQueryRepository 참조. */
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
    ): SalesSummaryResponse =
        summaryRepository.summarize(
            TenantContext.currentUserId(),
            month,
            startDate,
            endDate,
            categories,
            payments,
            channels,
            search,
        )

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
        sale.customerId = customerLinker.resolve(userId, request.customerId, request.customerName, request.customerPhone)
        sale.memo = request.memo
        val saved = saleRepository.save(sale)
        // recomputeGrade 는 raw JDBC 로 sales 를 집계하므로, INSERT 를 DB 에 반영(flush)한 뒤 재계산해야 한다.
        saved.customerId?.let {
            saleRepository.flush()
            gradeService.recomputeGrade(it)
        }
        return single(saved)
    }

    @Transactional
    fun update(
        id: Long,
        request: SaleUpdateRequest,
    ): SaleResponse {
        val userId = TenantContext.currentUserId()
        val sale = load(id)
        val oldCustomerId = sale.customerId
        request.date?.let { sale.date = it }
        request.amount?.let { sale.amount = it }
        request.categoryId?.let { sale.categoryId = labelReader.requireOwned(it, LabelDomains.SALE, LabelKinds.CATEGORY) }
        request.channelId?.let { sale.channelId = labelReader.requireOwned(it, LabelDomains.SALE, LabelKinds.CHANNEL) }
        request.customerName?.let { sale.customerName = it }
        request.customerPhone?.let { sale.customerPhone = it }
        if (request.customerId != null) {
            customerLinker.verifyOwnership(userId, request.customerId)
            sale.customerId = request.customerId
        } else if (request.customerName != null && request.customerPhone != null) {
            // 이름·전화를 함께 수정할 때만 customerId를 전화번호 기준으로 재해석(생성과 동일 규칙).
            // 한쪽만 수정하면 기존 연결을 그대로 유지한다(묵시적 언링크·고객 교체 방지).
            sale.customerId = customerLinker.resolve(userId, null, sale.customerName, sale.customerPhone)
        }
        request.memo?.let { sale.memo = it }
        request.hasReview?.let { sale.hasReview = it }
        unpaidService.applyTransition(sale, request)
        val saved = saleRepository.save(sale)
        syncLinkedReservations(saved)
        // recomputeGrade 는 raw JDBC 로 sales 를 집계하므로, UPDATE 를 DB 에 반영(flush)한 뒤 재계산해야 한다.
        // 고객 연결이 바뀌면 이전·신규 고객 모두 영향(구매횟수 이동) → 각각 1회씩 재계산(중복 제거).
        val affectedCustomers = setOfNotNull(oldCustomerId, saved.customerId)
        if (affectedCustomers.isNotEmpty()) {
            saleRepository.flush()
            affectedCustomers.forEach { gradeService.recomputeGrade(it) }
        }
        return single(saved)
    }

    /**
     * 매출과 예약(픽업)이 중복 보유하는 데이터를 예약 쪽에 동기화한다.
     * 고객명·연락처는 항상, 금액은 픽업이 1건일 때만(여러 픽업은 캘린더에서 픽업별로 관리).
     */
    private fun syncLinkedReservations(sale: Sale) {
        val saleId = sale.id ?: return
        val pickups = reservationRepository.findByUserIdAndSaleIdOrderByDateAsc(sale.userId, saleId)
        if (pickups.isEmpty()) return
        pickups.forEach {
            it.customerName = sale.customerName ?: ""
            it.customerPhone = sale.customerPhone
        }
        if (pickups.size == 1) pickups[0].amount = sale.amount
        reservationRepository.saveAll(pickups)
    }

    @Transactional
    fun delete(id: Long) {
        val sale = load(id)
        val customerId = sale.customerId // 삭제 전 캡처(삭제 후엔 참조 불가).
        // FK 미사용: 이 매출을 참조하던 예약·사진 카드의 sale_id를 직접 NULL 처리(둘 다 보존).
        reservationRepository.clearSaleReference(sale.userId, id)
        photoCardRepository.clearSaleReference(sale.userId, id)
        // recomputeGrade 는 raw JDBC 로 sales 를 집계하므로, 삭제를 DB에 반영(flush)한 뒤 재계산해야 한다.
        saleRepository.delete(sale)
        // 구매횟수 감소 → 연결 고객 등급 자동 재계산(잠금 아니면 강등 가능).
        customerId?.let {
            saleRepository.flush()
            gradeService.recomputeGrade(it)
        }
    }

    private fun single(sale: Sale): SaleResponse = assembler.single(sale)

    private fun load(id: Long): Sale =
        saleRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "매출을 찾을 수 없습니다")

    private fun requirePaymentId(id: Long?): Long = id ?: throw AppException(CommonErrorCode.VALIDATION, "결제방식은 필수입니다(미수가 아니면 결제수단을 지정하세요)")
}
