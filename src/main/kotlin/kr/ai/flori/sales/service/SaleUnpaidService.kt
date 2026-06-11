package kr.ai.flori.sales.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.sales.dto.SaleResponse
import kr.ai.flori.sales.dto.SaleUpdateRequest
import kr.ai.flori.sales.entity.Sale
import kr.ai.flori.sales.repository.SaleRepository
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 미수(외상) 상태 전이의 단일 소유자. 미수는 payment_method_id=NULL + is_unpaid=true 로 표현한다.
 * is_unpaid 마커는 완료 후에도 유지된다('미수였던 건' 추적용) — 정산 여부는 payment_method_id 로 판단.
 */
@Service
class SaleUnpaidService(
    private val saleRepository: SaleRepository,
    private val labelReader: LabelSettingReader,
    private val assembler: SaleResponseAssembler,
) {
    /** 미수 완료: 실제 결제수단 확정(미수 마커 is_unpaid는 유지 — '미수였던 건' 추적용). */
    @Transactional
    fun complete(
        id: Long,
        paymentMethodId: Long,
    ): SaleResponse {
        val sale = load(id)
        if (!sale.isUnpaid) throw AppException(CommonErrorCode.VALIDATION, "미수 매출이 아닙니다")
        sale.paymentMethodId = labelReader.requireOwned(paymentMethodId, LabelDomains.SALE, LabelKinds.PAYMENT)
        return assembler.single(saleRepository.save(sale))
    }

    /** 미수 정산 취소: 결제수단을 비워 다시 미정산 상태로 되돌린다. */
    @Transactional
    fun revert(id: Long): SaleResponse {
        val sale = load(id)
        if (!sale.isUnpaid) throw AppException(CommonErrorCode.VALIDATION, "미수 매출이 아닙니다")
        sale.paymentMethodId = null
        return assembler.single(saleRepository.save(sale))
    }

    /**
     * 수정 시 미수 상태 전이. is_unpaid 마커 + payment_method_id(정산 여부)를 함께 반영한다.
     * - isUnpaid=true  : 미수로 되돌림(결제수단 비움 → 매출 합계 제외)
     * - isUnpaid=false : 결제 완료로 전환(마커 OFF + 결제수단 확정)
     * - isUnpaid=null  : 미수 상태 불변, 결제수단만(제공 시) 반영
     */
    fun applyTransition(
        sale: Sale,
        request: SaleUpdateRequest,
    ) {
        if (request.isUnpaid == true) {
            sale.isUnpaid = true
            sale.paymentMethodId = null
            return
        }
        if (request.isUnpaid == false) sale.isUnpaid = false
        request.paymentMethodId?.let {
            sale.paymentMethodId = labelReader.requireOwned(it, LabelDomains.SALE, LabelKinds.PAYMENT)
        }
    }

    private fun load(id: Long): Sale =
        saleRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "매출을 찾을 수 없습니다")
}
