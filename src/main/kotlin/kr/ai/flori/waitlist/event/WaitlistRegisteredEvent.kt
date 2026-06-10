package kr.ai.flori.waitlist.event

/**
 * 사전등록 완료 이벤트. WaitlistService가 발행하고
 * 리스너가 AFTER_COMMIT 시점에 Discord 사전등록 알림을 보낸다.
 */
data class WaitlistRegisteredEvent(
    val email: String,
    val shopName: String,
    val count: Long,
    val capacity: Int,
)
