package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.BanCreateRequest
import kr.ai.flori.admin.dto.BanResponse
import kr.ai.flori.admin.dto.ReportQueueItem
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import kr.ai.flori.community.entity.CommunityBan
import kr.ai.flori.community.entity.CommunityComment
import kr.ai.flori.community.entity.CommunityPost
import kr.ai.flori.community.entity.CommunityReport
import kr.ai.flori.community.repository.CommunityBanRepository
import kr.ai.flori.community.repository.CommunityCommentRepository
import kr.ai.flori.community.repository.CommunityPostRepository
import kr.ai.flori.community.repository.CommunityReportRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 운영 콘솔 커뮤니티 모더레이션. cross-tenant — @RequiresAdmin 하위에서만 호출된다.
 * 모든 변경 액션은 같은 트랜잭션에서 감사 로그(AdminAuditService)를 남긴다.
 * 운영자는 숨김/삭제 글·댓글에도 접근해야 하므로 엔티티는 findById 로 직접 로드한다.
 */
@Service
class AdminModerationService(
    private val jdbc: JdbcTemplate,
    private val reportRepository: CommunityReportRepository,
    private val banRepository: CommunityBanRepository,
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository,
    private val audit: AdminAuditService,
) {
    // ── 신고 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listReports(
        status: String?,
        page: Int,
        size: Int,
    ): List<ReportQueueItem> {
        val reports =
            reportRepository
                .search(status?.takeIf { it.isNotBlank() }, Paging.pageSize(page, size, MAX_PAGE_SIZE))
                .content
        return reports.map { it.toQueueItem() }
    }

    @Transactional
    fun resolveReport(
        reportId: Long,
        resolution: String,
    ): ReportQueueItem {
        if (resolution !in RESOLUTIONS) throw AppException(CommonErrorCode.VALIDATION)
        val report = reportRepository.findById(reportId).orElseThrow { AppException(AdminErrorCode.REPORT_NOT_FOUND) }
        val actorId = TenantContext.currentUserId()
        when (resolution) {
            RESOLUTION_DELETED -> deleteTarget(report)
            RESOLUTION_HIDDEN -> hideTarget(report, actorId)
            // dismissed: 대상에 변화 없음.
        }
        report.resolve(actorId, resolution)
        reportRepository.save(report)
        audit.record(
            action = "REPORT_RESOLVE",
            targetType = "report",
            targetId = reportId.toString(),
            summary = "신고 처리($resolution): ${report.targetType}#${report.targetId}",
            metadata = mapOf("resolution" to resolution, "targetType" to report.targetType, "targetId" to report.targetId),
        )
        return report.toQueueItem()
    }

    // ── 게시글 모더레이션 ─────────────────────────────────────────────────

    @Transactional
    fun hidePost(postId: Long) {
        val post = loadPost(postId)
        post.hide(TenantContext.currentUserId())
        postRepository.save(post)
        audit.record("POST_HIDE", targetType = "post", targetId = postId.toString(), summary = "게시글 숨김")
    }

    @Transactional
    fun unhidePost(postId: Long) {
        val post = loadPost(postId)
        post.unhide()
        postRepository.save(post)
        audit.record("POST_UNHIDE", targetType = "post", targetId = postId.toString(), summary = "게시글 숨김 해제")
    }

    @Transactional
    fun deletePost(postId: Long) {
        val post = loadPost(postId)
        post.deletedAt = Instant.now()
        postRepository.save(post)
        audit.record("POST_DELETE", targetType = "post", targetId = postId.toString(), summary = "게시글 삭제")
    }

    // ── 댓글 모더레이션 ──────────────────────────────────────────────────

    @Transactional
    fun hideComment(commentId: Long) {
        val comment = loadComment(commentId)
        comment.hide(TenantContext.currentUserId())
        commentRepository.save(comment)
        audit.record("COMMENT_HIDE", targetType = "comment", targetId = commentId.toString(), summary = "댓글 숨김")
    }

    @Transactional
    fun unhideComment(commentId: Long) {
        val comment = loadComment(commentId)
        comment.unhide()
        commentRepository.save(comment)
        audit.record("COMMENT_UNHIDE", targetType = "comment", targetId = commentId.toString(), summary = "댓글 숨김 해제")
    }

    @Transactional
    fun deleteComment(commentId: Long) {
        val comment = loadComment(commentId)
        comment.deletedAt = Instant.now()
        commentRepository.save(comment)
        audit.record("COMMENT_DELETE", targetType = "comment", targetId = commentId.toString(), summary = "댓글 삭제")
    }

    // ── 차단(밴) ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listBans(
        page: Int,
        size: Int,
    ): List<BanResponse> =
        banRepository
            .findActive(Paging.pageSize(page, size, MAX_PAGE_SIZE))
            .content
            .map { it.toResponse() }

    @Transactional
    fun createBan(request: BanCreateRequest): BanResponse {
        val userId = requireNotNull(request.userId)
        val existing = banRepository.findByUserIdAndLiftedAtIsNull(userId)
        if (existing != null && existing.isActive()) throw AppException(AdminErrorCode.ALREADY_BANNED)
        val ban =
            CommunityBan(
                userId = userId,
                bannedBy = TenantContext.currentUserId(),
            )
        ban.reason = request.reason
        ban.expiresAt = request.expiresAt
        val saved = banRepository.save(ban)
        audit.record(
            action = "USER_BAN",
            targetType = "user",
            targetId = userId.toString(),
            summary = "커뮤니티 활동 차단",
            metadata = mapOf("reason" to request.reason, "expiresAt" to request.expiresAt?.toString()),
        )
        return saved.toResponse()
    }

    @Transactional
    fun liftBan(banId: Long): BanResponse {
        val ban = banRepository.findById(banId).orElseThrow { AppException(AdminErrorCode.BAN_NOT_FOUND) }
        ban.lift()
        banRepository.save(ban)
        audit.record("USER_UNBAN", targetType = "user", targetId = ban.userId.toString(), summary = "커뮤니티 차단 해제")
        return ban.toResponse()
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────

    private fun deleteTarget(report: CommunityReport) {
        when (report.targetType) {
            CommunityReport.TARGET_POST -> deletePost(report.targetId)
            CommunityReport.TARGET_COMMENT -> deleteComment(report.targetId)
            else -> throw AppException(CommonErrorCode.VALIDATION)
        }
    }

    private fun hideTarget(
        report: CommunityReport,
        actorId: Long,
    ) {
        when (report.targetType) {
            CommunityReport.TARGET_POST -> {
                val post = loadPost(report.targetId)
                post.hide(actorId)
                postRepository.save(post)
            }
            CommunityReport.TARGET_COMMENT -> {
                val comment = loadComment(report.targetId)
                comment.hide(actorId)
                commentRepository.save(comment)
            }
            else -> throw AppException(CommonErrorCode.VALIDATION)
        }
    }

    private fun loadPost(postId: Long): CommunityPost =
        postRepository.findById(postId).orElseThrow { AppException(AdminErrorCode.POST_NOT_FOUND) }

    private fun loadComment(commentId: Long): CommunityComment =
        commentRepository.findById(commentId).orElseThrow { AppException(AdminErrorCode.COMMENT_NOT_FOUND) }

    // 신고 큐 항목 매핑: 대상 미리보기·작성자·동일 대상 누적 신고 수를 JdbcTemplate로 채운다.
    private fun CommunityReport.toQueueItem(): ReportQueueItem {
        val (preview, authorUserId) = targetMeta(targetType, targetId)
        val reportCount =
            reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, CommunityReport.STATUS_PENDING)
        return ReportQueueItem(
            id = id!!,
            targetType = targetType,
            targetId = targetId,
            reporterUserId = reporterUserId,
            reason = reason,
            detail = detail,
            status = status,
            resolution = resolution,
            reportCount = reportCount,
            targetPreview = preview,
            authorUserId = authorUserId,
            resolvedBy = resolvedBy,
            resolvedAt = resolvedAt,
            createdAt = createdAt,
        )
    }

    // 대상 미리보기/작성자 조회(삭제·숨김 무관 — 운영자는 항상 본다). 대상이 없으면 (null, null).
    private fun targetMeta(
        targetType: String,
        targetId: Long,
    ): Pair<String?, Long?> {
        val sql =
            when (targetType) {
                CommunityReport.TARGET_POST ->
                    "SELECT LEFT(COALESCE(NULLIF(content_text, ''), title), $PREVIEW_LEN) AS preview, " +
                        "author_user_id FROM community_posts WHERE id = ?"
                CommunityReport.TARGET_COMMENT ->
                    "SELECT LEFT(content, $PREVIEW_LEN) AS preview, author_user_id FROM community_comments WHERE id = ?"
                else -> return null to null
            }
        return jdbc
            .query(sql, { rs, _ -> rs.getString("preview") to rs.getLong("author_user_id") }, targetId)
            .firstOrNull() ?: (null to null)
    }

    private fun CommunityBan.toResponse() =
        BanResponse(
            id = id!!,
            userId = userId,
            reason = reason,
            bannedBy = bannedBy,
            expiresAt = expiresAt,
            liftedAt = liftedAt,
            createdAt = createdAt,
        )

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val PREVIEW_LEN = 100
        const val RESOLUTION_DELETED = "deleted"
        const val RESOLUTION_HIDDEN = "hidden"
        const val RESOLUTION_DISMISSED = "dismissed"
        val RESOLUTIONS = setOf(RESOLUTION_DELETED, RESOLUTION_HIDDEN, RESOLUTION_DISMISSED)
    }
}
