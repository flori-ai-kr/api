package kr.ai.flori.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.admin.dto.AdminAuditLogResponse
import kr.ai.flori.admin.entity.AdminAuditLog
import kr.ai.flori.admin.repository.AdminAuditLogRepository
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import kr.ai.flori.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영자 감사 로그. 모든 운영자 액션을 append-only로 기록한다(책임추적/컴플라이언스).
 * record()는 액션 수행 서비스가 같은 트랜잭션에서 호출한다 — 액션과 로그가 함께 커밋된다.
 * actor는 TenantContext(현재 운영자)에서 얻고 email은 스냅샷으로 비정규화 저장한다.
 */
@Service
class AdminAuditService(
    private val repository: AdminAuditLogRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    /** 운영자 액션 기록. metadata는 {before, after, reason, ...} 자유 맵. */
    @Transactional
    fun record(
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        summary: String? = null,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        val actorId = TenantContext.currentUserId()
        val actorEmail = userRepository.findById(actorId).orElse(null)?.email
        val log =
            AdminAuditLog(actorUserId = actorId, action = action).apply {
                this.actorEmail = actorEmail
                this.targetType = targetType
                this.targetId = targetId
                this.summary = summary
                this.metadata = objectMapper.valueToTree(metadata)
            }
        repository.save(log)
    }

    @Transactional(readOnly = true)
    fun list(
        action: String?,
        actorUserId: Long?,
        page: Int,
        size: Int,
    ): List<AdminAuditLogResponse> =
        repository
            .search(
                action?.takeIf { it.isNotBlank() },
                actorUserId,
                Paging.pageSize(page, size, MAX_PAGE_SIZE),
            ).content
            .map { it.toResponse() }

    private fun AdminAuditLog.toResponse() =
        AdminAuditLogResponse(
            id = id!!,
            actorUserId = actorUserId,
            actorEmail = actorEmail,
            action = action,
            targetType = targetType,
            targetId = targetId,
            summary = summary,
            metadata = metadata,
            createdAt = createdAt,
        )

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
