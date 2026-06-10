package kr.ai.flori.interview.event

/**
 * 유저 인터뷰 신청 완료 이벤트. InterviewService가 발행하고
 * 리스너가 AFTER_COMMIT 시점에 Discord 인터뷰 알림을 보낸다.
 */
data class InterviewRequestedEvent(
    val name: String,
    val phone: String,
)
