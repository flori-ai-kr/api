package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.BroadcastCreateRequest
import kr.ai.flori.admin.dto.BroadcastResponse
import kr.ai.flori.admin.dto.SegmentPreviewResponse
import kr.ai.flori.admin.entity.Broadcast
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.admin.repository.BroadcastRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 운영 콘솔 푸시 브로드캐스트. cross-tenant — @RequiresAdmin 하위에서만 호출된다.
 * 세그먼트를 user id 목록으로 해석해 사용자별 PushDispatcher로 발송하고, 발송/감사 로그를 남긴다.
 * 발송 전(draft/scheduled)에만 수정/삭제가 가능하다.
 */
@Service
class AdminBroadcastService(
    private val jdbc: JdbcTemplate,
    private val repository: BroadcastRepository,
    private val pushDispatcher: PushDispatcher,
    private val notificationSendLogService: NotificationSendLogService,
    private val audit: AdminAuditService,
) {
    @Transactional(readOnly = true)
    fun list(
        status: String?,
        page: Int,
        size: Int,
    ): List<BroadcastResponse> =
        repository
            .search(
                status?.takeIf { it.isNotBlank() },
                Paging.pageSize(page, size, MAX_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")),
            ).content
            .map { it.toResponse() }

    @Transactional
    fun create(req: BroadcastCreateRequest): BroadcastResponse {
        val segment = (req.segment ?: SEGMENT_ALL).also { requireValidSegment(it) }
        val broadcast =
            Broadcast(
                title = req.title,
                body = req.body,
                createdBy = TenantContext.currentUserId(),
            ).apply {
                this.deepLink = req.deepLink
                this.segment = segment
                this.scheduledAt = req.scheduledAt
                this.status = if (req.scheduledAt != null) STATUS_SCHEDULED else STATUS_DRAFT
            }
        repository.save(broadcast)
        return broadcast.toResponse()
    }

    @Transactional(readOnly = true)
    fun previewSegment(segment: String): SegmentPreviewResponse {
        requireValidSegment(segment)
        val count =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM (${segmentSql(segment)}) t",
                Long::class.java,
            ) ?: 0
        return SegmentPreviewResponse(segment = segment, targetCount = count)
    }

    // 의도적으로 @Transactional 미부여: 푸시 발송(외부 I/O) 루프가 DB 트랜잭션/커넥션을 점유하지 않도록 한다.
    // 대상 조회·entity 저장·로그·감사는 각자(또는 repository.save) 자체 트랜잭션으로 원자 처리된다.
    // (LLM/외부 호출은 트랜잭션 밖에서 — 프로젝트 컨벤션)
    fun send(id: Long): BroadcastResponse {
        val broadcast = load(id)
        if (broadcast.status !in SENDABLE_STATES) {
            throw AppException(AdminErrorCode.INVALID_BROADCAST_STATE)
        }
        val segment = broadcast.segment
        val userIds = jdbc.queryForList(segmentSql(segment), Long::class.java)
        var sentCount = 0
        var failedCount = 0
        userIds.forEach { uid ->
            val delivered =
                pushDispatcher.sendToUser(uid, broadcast.title, broadcast.body, broadcast.deepLink, PushTypes.BROADCAST)
            if (delivered > 0) sentCount++ else failedCount++
        }
        broadcast.status = STATUS_SENT
        broadcast.sentAt = Instant.now()
        broadcast.targetCount = userIds.size
        broadcast.sentCount = sentCount
        broadcast.failedCount = failedCount
        repository.save(broadcast)
        notificationSendLogService.record(
            source = "web",
            type = "broadcast",
            sentCount = sentCount,
            failedCount = failedCount,
            title = broadcast.title,
            body = broadcast.body,
            segment = segment,
            broadcastId = id,
            actorUserId = TenantContext.currentUserId(),
        )
        audit.record(
            action = "BROADCAST_SEND",
            targetType = "broadcast",
            targetId = id.toString(),
            summary = "${broadcast.title} 브로드캐스트 발송",
            metadata = mapOf("segment" to segment, "sent" to sentCount, "failed" to failedCount),
        )
        return broadcast.toResponse()
    }

    @Transactional
    fun delete(id: Long) {
        val broadcast = load(id)
        if (broadcast.status !in SENDABLE_STATES) {
            throw AppException(AdminErrorCode.INVALID_BROADCAST_STATE)
        }
        repository.delete(broadcast)
        audit.record(
            action = "BROADCAST_DELETE",
            targetType = "broadcast",
            targetId = id.toString(),
            summary = "${broadcast.title} 브로드캐스트 삭제",
            metadata = mapOf("segment" to broadcast.segment, "status" to broadcast.status),
        )
    }

    private fun load(id: Long): Broadcast = repository.findById(id).orElseThrow { AppException(AdminErrorCode.BROADCAST_NOT_FOUND) }

    private fun requireValidSegment(segment: String) {
        if (segment !in SEGMENT_SQL) throw AppException(CommonErrorCode.VALIDATION)
    }

    private fun segmentSql(segment: String): String = SEGMENT_SQL[segment] ?: throw AppException(CommonErrorCode.VALIDATION)

    private fun Broadcast.toResponse() =
        BroadcastResponse(
            id = id!!,
            title = title,
            body = body,
            deepLink = deepLink,
            segment = segment,
            status = status,
            scheduledAt = scheduledAt,
            sentAt = sentAt,
            targetCount = targetCount,
            sentCount = sentCount,
            failedCount = failedCount,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private companion object {
        const val MAX_PAGE_SIZE = 200
        const val SEGMENT_ALL = "all"
        const val STATUS_DRAFT = "draft"
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_SENT = "sent"
        val SENDABLE_STATES = setOf(STATUS_DRAFT, STATUS_SCHEDULED)

        val SEGMENT_SQL =
            mapOf(
                "all" to "SELECT id FROM users WHERE is_active",
                "verified" to
                    "SELECT DISTINCT u.id FROM users u " +
                    "JOIN business_verifications bv ON bv.user_id = u.id AND bv.status = 'APPROVED' " +
                    "WHERE u.is_active",
                "active_7d" to
                    "SELECT DISTINCT u.id FROM users u " +
                    "JOIN sales s ON s.user_id = u.id AND s.date >= CURRENT_DATE - INTERVAL '7 days' " +
                    "WHERE u.is_active",
                "dormant_14d" to
                    "SELECT u.id FROM users u WHERE u.is_active " +
                    "AND NOT EXISTS (SELECT 1 FROM sales s WHERE s.user_id = u.id " +
                    "AND s.date >= CURRENT_DATE - INTERVAL '14 days')",
                "ai_unused" to
                    "SELECT u.id FROM users u WHERE u.is_active " +
                    "AND NOT EXISTS (SELECT 1 FROM ai_marketing_content m WHERE m.user_id = u.id " +
                    "AND m.deleted_at IS NULL)",
            )
    }
}
