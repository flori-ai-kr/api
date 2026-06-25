package kr.ai.flori.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/** 운영 콘솔 사업자 인증 신청 1건(유저 정보·등록증 URL 포함). */
data class AdminVerificationResponse(
    val id: Long,
    val userId: Long,
    val businessNumber: String,
    val businessName: String,
    val representativeName: String,
    val businessLicenseUrl: String,
    val status: String,
    val rejectReason: String?,
    val submittedAt: Instant?,
    val reviewedAt: Instant?,
)

data class AdminVerificationRejectRequest(
    @field:NotBlank(message = "거절 사유는 필수입니다")
    @field:Size(max = 500, message = "거절 사유는 500자 이하여야 합니다")
    val reason: String,
)
