package kr.ai.flori.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

/**
 * 신고 큐 1건. 대상(게시글/댓글) 미리보기·작성자·동일 대상 누적 신고 수를 함께 제공한다.
 * targetPreview/authorUserId 는 대상이 이미 삭제됐으면 null 일 수 있다.
 */
data class ReportQueueItem(
    val id: Long,
    val targetType: String,
    val targetId: Long,
    val reporterUserId: Long,
    val reason: String,
    val detail: String?,
    val status: String,
    val resolution: String?,
    val reportCount: Long,
    val targetPreview: String?,
    val authorUserId: Long?,
    val resolvedBy: Long?,
    val resolvedAt: Instant?,
    val createdAt: Instant,
)

/** 신고 처리 요청. resolution: 'deleted'(대상 삭제) | 'hidden'(대상 숨김) | 'dismissed'(기각). */
data class ResolveReportRequest(
    @field:NotBlank(message = "처리 방식(resolution)은 필수입니다")
    val resolution: String?,
)

data class BanResponse(
    val id: Long,
    val userId: Long,
    val reason: String?,
    val bannedBy: Long,
    val expiresAt: Instant?,
    val liftedAt: Instant?,
    val createdAt: Instant,
)

data class BanCreateRequest(
    @field:NotNull(message = "차단 대상 userId는 필수입니다")
    val userId: Long?,
    val reason: String? = null,
    val expiresAt: Instant? = null,
)
