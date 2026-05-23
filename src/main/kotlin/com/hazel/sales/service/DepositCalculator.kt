package com.hazel.sales.service

import com.hazel.common.domain.DepositStatuses
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * 매출의 입금 관련 값(수수료/입금예정액/입금예정일/입금상태)을 서버에서 계산한다.
 * 카드 결제는 사용자의 card_company_settings(fee_rate, deposit_days)를 SSOT로 사용.
 */
@Component
class DepositCalculator(
    private val jdbcTemplate: JdbcTemplate,
) {
    data class Deposit(
        val fee: Int?,
        val expectedDeposit: Int?,
        val expectedDepositDate: LocalDate?,
        val depositStatus: String,
    )

    fun calculate(
        userId: UUID,
        date: LocalDate,
        amount: Int,
        paymentMethod: String,
        cardCompany: String?,
    ): Deposit {
        // 카드가 아니면 입금대조 대상 아님
        if (paymentMethod != PAYMENT_CARD || cardCompany.isNullOrBlank()) {
            return Deposit(null, null, null, DepositStatuses.NOT_APPLICABLE)
        }
        val setting =
            findCardSetting(userId, cardCompany)
                ?: return Deposit(null, amount, null, DepositStatuses.PENDING)

        val fee = computeFee(amount, setting.feeRate)
        return Deposit(
            fee = fee,
            expectedDeposit = amount - fee,
            expectedDepositDate = addBusinessDays(date, setting.depositDays),
            depositStatus = DepositStatuses.PENDING,
        )
    }

    // settings 도메인 테이블(card_company_settings)을 교차 조회한다 — 수수료 계산(SSOT) 핫패스라
    // 단일 컬럼 네이티브 조회로 유지. user_id 바인딩으로 테넌트 격리(컬럼 변경 시 이 SQL도 함께 갱신).
    private fun findCardSetting(
        userId: UUID,
        cardCompany: String,
    ): CardSetting? =
        jdbcTemplate
            .query(
                "SELECT fee_rate, deposit_days FROM card_company_settings " +
                    "WHERE user_id = ?::uuid AND name = ? AND is_active = TRUE LIMIT 1",
                { rs, _ -> CardSetting(rs.getBigDecimal("fee_rate"), rs.getInt("deposit_days")) },
                userId,
                cardCompany,
            ).firstOrNull()

    private data class CardSetting(
        val feeRate: BigDecimal,
        val depositDays: Int,
    )

    private companion object {
        const val PAYMENT_CARD = "card"
    }
}
