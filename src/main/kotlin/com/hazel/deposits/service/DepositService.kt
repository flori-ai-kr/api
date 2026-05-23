package com.hazel.deposits.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.deposits.dto.DepositSummaryResponse
import com.hazel.deposits.repository.DepositSpecifications
import com.hazel.sales.dto.SaleResponse
import com.hazel.sales.entity.Sale
import com.hazel.sales.repository.SaleRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 입금대조: 카드 매출의 입금 상태(pending/completed) 확인·되돌리기.
 * sales 테이블을 다루며 모든 쿼리는 TenantContext userId로 격리(HARD).
 */
@Service
class DepositService(
    private val saleRepository: SaleRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        month: String?,
        status: String?,
        cardCompany: String?,
    ): List<SaleResponse> {
        val spec = DepositSpecifications.filter(TenantContext.currentUserId(), month, status, cardCompany)
        return saleRepository
            .findAll(spec, Sort.by(Sort.Order.desc("date")))
            .map(SaleResponse::from)
    }

    @Transactional(readOnly = true)
    fun summary(month: String?): DepositSummaryResponse {
        val spec = DepositSpecifications.filter(TenantContext.currentUserId(), month, null, null)
        var pendingCount = 0
        var pendingAmount = 0L
        var completedCount = 0
        var completedAmount = 0L
        saleRepository.findAll(spec).forEach { sale ->
            val amount = (sale.expectedDeposit ?: sale.amount).toLong()
            when (sale.depositStatus) {
                STATUS_PENDING -> {
                    pendingCount += 1
                    pendingAmount += amount
                }
                STATUS_COMPLETED -> {
                    completedCount += 1
                    completedAmount += amount
                }
            }
        }
        return DepositSummaryResponse(pendingCount, pendingAmount, completedCount, completedAmount)
    }

    @Transactional
    fun confirm(id: UUID): SaleResponse {
        val sale = load(id)
        markCompleted(sale)
        return SaleResponse.from(saleRepository.save(sale))
    }

    /** 다건 확인: 본인 소유 매출만 일괄 완료 처리(타 테넌트 ID는 무시). */
    @Transactional
    fun confirmMultiple(ids: List<UUID>): Int {
        if (ids.isEmpty()) return 0
        val sales = saleRepository.findByUserIdAndIdIn(TenantContext.currentUserId(), ids)
        sales.forEach { markCompleted(it) }
        saleRepository.saveAll(sales)
        return sales.size
    }

    @Transactional
    fun revert(id: UUID): SaleResponse {
        val sale = load(id)
        sale.depositStatus = STATUS_PENDING
        sale.depositedAt = null
        sale.updatedAt = Instant.now()
        return SaleResponse.from(saleRepository.save(sale))
    }

    private fun markCompleted(sale: Sale) {
        sale.depositStatus = STATUS_COMPLETED
        sale.depositedAt = Instant.now()
        sale.updatedAt = Instant.now()
    }

    private fun load(id: UUID): Sale =
        saleRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "매출을 찾을 수 없습니다")

    private companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_COMPLETED = "completed"
    }
}
