package kr.ai.flori.verification.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

/** 등록증 업로드 타깃(presigned PUT) 요청. */
data class BusinessLicenseUploadTargetRequest(
    @field:NotBlank(message = "contentType이 필요합니다")
    val contentType: String?,
)

/** 등록증 업로드 타깃 응답. */
data class BusinessLicenseUploadTargetResponse(
    val uploadUrl: String,
    val fileUrl: String,
    val expiresInSeconds: Long,
)

/** 사업자 인증 신청 요청. businessNumber는 하이픈 제거 10자리. */
data class BusinessVerificationSubmitRequest(
    @field:Pattern(regexp = "\\d{10}", message = "사업자번호는 숫자 10자리여야 합니다")
    val businessNumber: String,
    @field:NotBlank(message = "상호가 필요합니다")
    @field:Size(max = 255)
    val businessName: String,
    @field:NotBlank(message = "대표자명이 필요합니다")
    @field:Size(max = 100)
    val representativeName: String,
    @field:NotBlank(message = "등록증 URL이 필요합니다")
    val businessLicenseUrl: String,
)

/**
 * 사업자 인증 상태 응답. 이력이 없으면 status="NONE"(200).
 * 모바일 분기: NONE→인증화면, PENDING→대기, REJECTED→사유+재신청, APPROVED→잠금해제.
 */
data class BusinessVerificationResponse(
    val status: String,
    val rejectReason: String? = null,
    val submittedAt: Instant? = null,
    val reviewedAt: Instant? = null,
) {
    companion object {
        const val STATUS_NONE = "NONE"

        fun none(): BusinessVerificationResponse = BusinessVerificationResponse(status = STATUS_NONE)
    }
}
