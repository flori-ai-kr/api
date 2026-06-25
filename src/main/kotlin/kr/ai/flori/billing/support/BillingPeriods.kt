package kr.ai.flori.billing.support

import java.time.ZonedDateTime

/** 플랜별 다음 주기 계산. MONTHLY=+1개월, YEARLY=+1년. */
object BillingPeriods {
    fun next(
        plan: String,
        from: ZonedDateTime,
    ): ZonedDateTime =
        when (plan) {
            "MONTHLY" -> from.plusMonths(1)
            "YEARLY" -> from.plusYears(1)
            else -> error("Unknown plan: $plan")
        }
}
