package kr.ai.flori.billing.event

data class SubscriptionExpiredEvent(
    val userId: Long,
    val subscriptionId: Long,
    val reason: String,
)
