package kr.ai.flori.auth.event

/**
 * 신규 가입(register/complete) 완료 이벤트. AuthService가 발행하고
 * 리스너가 AFTER_COMMIT 시점에 Discord 가입 알림을 보낸다.
 */
data class UserRegisteredEvent(
    val userId: Long,
    val nickname: String,
    val provider: String,
)
