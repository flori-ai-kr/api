package kr.ai.flori.support.dto

import jakarta.validation.constraints.NotBlank
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.support.entity.SupportInquiry
import java.time.Instant

// ── 화이트리스트 ───────────────────────────────────────────────────────────

/** 문의 카테고리 화이트리스트(DB CHECK 제약과 일치). */
val INQUIRY_CATEGORIES = setOf("bug", "feature", "account", "payment", "feedback", "etc")

/** 문의 상태 화이트리스트(DB CHECK 제약과 일치). */
val INQUIRY_STATUSES = setOf("open", "in_progress", "resolved", "closed")

/** 화이트리스트 외 카테고리면 검증 예외. */
fun validateInquiryCategory(category: String): String {
    if (category !in INQUIRY_CATEGORIES) throw AppException(CommonErrorCode.VALIDATION)
    return category
}

/** 화이트리스트 외 상태면 검증 예외. */
fun validateInquiryStatus(status: String): String {
    if (status !in INQUIRY_STATUSES) throw AppException(CommonErrorCode.VALIDATION)
    return status
}

// ── 요청 ──────────────────────────────────────────────────────────────────

data class InquiryCreateRequest(
    val category: String = "etc",
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String?,
    @field:NotBlank(message = "내용은 필수입니다")
    val body: String?,
    val imageUrls: List<String> = emptyList(),
)

data class InquiryAnswerRequest(
    @field:NotBlank(message = "답변은 필수입니다")
    val answer: String?,
    val status: String? = null,
)

data class InquiryStatusRequest(
    @field:NotBlank(message = "상태는 필수입니다")
    val status: String?,
)

// ── 응답 ──────────────────────────────────────────────────────────────────

data class InquiryResponse(
    val id: Long,
    val userId: Long,
    val category: String,
    val title: String,
    val body: String,
    val imageUrls: List<String>,
    val status: String,
    val answer: String?,
    val answeredBy: Long?,
    val answeredAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// ── 매핑 ──────────────────────────────────────────────────────────────────

fun SupportInquiry.toResponse() =
    InquiryResponse(
        id = id!!,
        userId = userId,
        category = category,
        title = title,
        body = body,
        imageUrls = imageUrls.toList(),
        status = status,
        answer = answer,
        answeredBy = answeredBy,
        answeredAt = answeredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// ── 업로드 ────────────────────────────────────────────────────────────────

data class InquiryUploadRequest(
    val files: List<InquiryFileInfo>,
) {
    data class InquiryFileInfo(
        val name: String,
        val type: String,
        val size: Long,
    )
}

data class InquiryUploadTargetResponse(
    val uploadUrl: String,
    val publicUrl: String,
    val originalName: String,
)
