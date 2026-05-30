package kr.ai.flori.admin.event

/** 운영자가 사업자 인증을 승인/거절했을 때 발행. approved=false면 reason 존재. */
data class BusinessVerificationReviewedEvent(
    val userId: Long,
    val businessName: String,
    val approved: Boolean,
    val reason: String?,
)
