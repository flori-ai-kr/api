package kr.ai.flori.verification.event

/**
 * 사업자 인증 신청 완료 이벤트. 서비스가 PENDING 저장 후 발행하고,
 * 리스너가 AFTER_COMMIT 시점에 Discord 알림을 보낸다.
 */
data class BusinessVerificationSubmittedEvent(
    val userId: Long,
    val businessName: String,
    val businessNumber: String,
    val representativeName: String,
    val businessLicenseUrl: String,
)
