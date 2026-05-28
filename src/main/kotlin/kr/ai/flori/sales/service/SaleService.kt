package kr.ai.flori.sales.service

import kr.ai.flori.common.domain.PaymentMethods
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.repository.CustomerRepository
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.dto.SaleResponse
import kr.ai.flori.sales.dto.SaleUpdateRequest
import kr.ai.flori.sales.dto.SalesPageResponse
import kr.ai.flori.sales.entity.Sale
import kr.ai.flori.sales.repository.SaleRepository
import kr.ai.flori.sales.repository.SaleSpecifications
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 매출 서비스. 모든 조회/변경은 TenantContext의 userId로 격리(멀티테넌시 HARD).
 */
@Service
class SaleService(
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        month: String?,
        offset: Int,
        limit: Int,
        categories: List<String>?,
        payments: List<String>?,
        channels: List<String>?,
        search: String?,
    ): SalesPageResponse {
        val userId = TenantContext.currentUserId()
        val spec = SaleSpecifications.filter(userId, month, categories, payments, channels, search)
        val sort = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt"))
        val pageable = PageRequest.of(offset / limit, limit, sort)
        val page = saleRepository.findAll(spec, pageable)
        return SalesPageResponse(page.content.map(SaleResponse::from), page.hasNext())
    }

    @Transactional(readOnly = true)
    fun get(id: Long): SaleResponse = SaleResponse.from(load(id))

    @Transactional(readOnly = true)
    fun suggestions(): List<String> = saleRepository.findNotesByFrequency(TenantContext.currentUserId())

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
                productName = category,
                productCategory = category,
                amount = amount,
                paymentMethod = paymentMethod,
            )
        sale.reservationChannel = request.reservationChannel ?: DEFAULT_CHANNEL
        sale.isUnpaid = isUnpaid
        sale.customerName = request.customerName
        sale.customerPhone = request.customerPhone
        sale.customerId = request.customerId
        sale.note = request.note
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
        request.productCategory?.let {
            sale.productCategory = it
            sale.productName = it
        }
        request.reservationChannel?.let { sale.reservationChannel = it }
        request.customerName?.let { sale.customerName = it }
        request.customerPhone?.let { sale.customerPhone = it }
        request.customerId?.let {
            verifyCustomerOwnership(userId, it)
            sale.customerId = it
        }
        request.note?.let { sale.note = it }
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
        saleRepository.delete(load(id))
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
    }
}
