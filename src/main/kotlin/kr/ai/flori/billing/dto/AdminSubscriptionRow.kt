package kr.ai.flori.billing.dto

import kr.ai.flori.billing.entity.Subscription
import java.time.Instant

data class AdminSubscriptionRow(
    val userId: Long,
    val plan: String,
    val status: String,
    val nextBillingAt: Instant,
    val currentPeriodEnd: Instant?,
    val cancelAtPeriodEnd: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(s: Subscription): AdminSubscriptionRow =
            AdminSubscriptionRow(
                userId = s.userId,
                plan = s.plan,
                status = s.status,
                nextBillingAt = s.nextBillingAt,
                currentPeriodEnd = s.currentPeriodEnd,
                cancelAtPeriodEnd = s.cancelAtPeriodEnd,
                createdAt = s.createdAt,
            )
    }
}
