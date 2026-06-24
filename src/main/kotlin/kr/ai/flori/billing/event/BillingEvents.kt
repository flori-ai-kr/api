package kr.ai.flori.billing.event

/** 구독 시작(체험 또는 즉시활성). 리스너가 디스코드+푸시 처리. */
data class SubscriptionStartedEvent(
    val userId: Long,
    val subscriptionId: Long,
    val plan: String,
    val amount: Int,
    val trial: Boolean,
)
