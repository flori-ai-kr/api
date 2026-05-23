package com.hazel.deposits.service

import com.hazel.common.domain.DepositStatuses
import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.common.util.monthRange
import com.hazel.deposits.dto.DepositSummaryResponse
import com.hazel.deposits.repository.DepositSpecifications
import com.hazel.sales.dto.SaleResponse
import com.hazel.sales.entity.Sale
import com.hazel.sales.repository.SaleRepository
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.Instant
import java.util.UUID

/**
 * 입금대조: 카드 매출의 입금 상태(pending/completed) 확인·되돌리기.
 * sales 테이블을 다루며 모든 쿼리는 TenantContext userId로 격리(HARD).
 */
@Service
class DepositService(
    private val saleRepository: SaleRepository,
    private val jdbcTemplate: JdbcTemplate,
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

    /** 요약: 네이티브 집계로 대기/완료 건수·예상입금액(expected_deposit ?: amount)을 산출. */
    @Transactional(readOnly = true)
    fun summary(month: String?): DepositSummaryResponse {
        val userId = TenantContext.currentUserId()
        val range = monthRange(month)
        val sql =
            buildString {
                append("SELECT deposit_status AS status, COUNT(*) AS cnt, ")
                append("COALESCE(SUM(COALESCE(expected_deposit, amount)), 0) AS amount ")
                append("FROM sales WHERE user_id = ?::uuid AND payment_method = 'card' ")
                if (range != null) append("AND date BETWEEN ? AND ? ")
                append("GROUP BY deposit_status")
            }
        val args =
            if (range != null) {
                arrayOf<Any>(userId, Date.valueOf(range.first), Date.valueOf(range.second))
            } else {
                arrayOf<Any>(userId)
            }

        var pendingCount = 0
        var pendingAmount = 0L
        var completedCount = 0
        var completedAmount = 0L
        jdbcTemplate
            .query(sql, { rs, _ -> Triple(rs.getString("status"), rs.getInt("cnt"), rs.getLong("amount")) }, *args)
            .forEach { (status, cnt, amount) ->
                when (status) {
                    DepositStatuses.PENDING -> {
                        pendingCount = cnt
                        pendingAmount = amount
                    }
                    DepositStatuses.COMPLETED -> {
                        completedCount = cnt
                        completedAmount = amount
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
        sale.depositStatus = DepositStatuses.PENDING
        sale.depositedAt = null
        sale.updatedAt = Instant.now()
        return SaleResponse.from(saleRepository.save(sale))
    }

    private fun markCompleted(sale: Sale) {
        sale.depositStatus = DepositStatuses.COMPLETED
        sale.depositedAt = Instant.now()
        sale.updatedAt = Instant.now()
    }

    private fun load(id: UUID): Sale =
        saleRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "매출을 찾을 수 없습니다")
}
