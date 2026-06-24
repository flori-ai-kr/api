package kr.ai.flori.admin.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

/** 운영 콘솔 브로드캐스트 1건. */
data class BroadcastResponse(
    val id: Long,
    val title: String,
    val body: String,
    val deepLink: String?,
    val segment: String,
    val status: String,
    val scheduledAt: Instant?,
    val sentAt: Instant?,
    val targetCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val createdBy: Long,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class BroadcastCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String,
    @field:NotBlank(message = "내용은 필수입니다")
    val body: String,
    val deepLink: String? = null,
    val segment: String? = "all",
    val scheduledAt: Instant? = null,
)

/** 세그먼트별 대상 인원 미리보기. */
data class SegmentPreviewResponse(
    val segment: String,
    val targetCount: Long,
)
