package kr.ai.flori.billing.event

data class PaymentChargedEvent(
    val userId: Long,
    val subscriptionId: Long,
    val amount: Int,
    val success: Boolean,
)
