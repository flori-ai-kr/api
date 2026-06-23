package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.NotificationSendLogResponse
import kr.ai.flori.admin.entity.NotificationSendLog
import kr.ai.flori.admin.repository.NotificationSendLogRepository
import kr.ai.flori.common.util.Paging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 발송 이력 기록/조회. 발송 측(브로드캐스트/리마인더 등)이 발송 직후 record()를 호출한다.
 * 발송 자체를 막지 않도록 기록 실패는 호출부에서 비차단 처리한다.
 */
@Service
class NotificationSendLogService(
    private val repository: NotificationSendLogRepository,
) {
    @Transactional
    @Suppress("LongParameterList")
    fun record(
        source: String,
        type: String,
        sentCount: Int,
        failedCount: Int,
        title: String? = null,
        body: String? = null,
        segment: String? = null,
        targetUserId: Long? = null,
        broadcastId: Long? = null,
        actorUserId: Long? = null,
        errorMessage: String? = null,
    ): NotificationSendLog {
        val status =
            when {
                sentCount == 0 && failedCount > 0 -> "failed"
                failedCount > 0 -> "partial"
                else -> "sent"
            }
        val log =
            NotificationSendLog(source = source, type = type).apply {
                this.segment = segment
                this.targetUserId = targetUserId
                this.title = title
                this.body = body
                this.status = status
                this.sentCount = sentCount
                this.failedCount = failedCount
                this.errorMessage = errorMessage
                this.broadcastId = broadcastId
                this.actorUserId = actorUserId
            }
        return repository.save(log)
    }

    @Transactional(readOnly = true)
    fun list(
        type: String?,
        source: String?,
        status: String?,
        page: Int,
        size: Int,
    ): List<NotificationSendLogResponse> =
        repository
            .search(
                type?.takeIf { it.isNotBlank() },
                source?.takeIf { it.isNotBlank() },
                status?.takeIf { it.isNotBlank() },
                Paging.pageSize(page, size, MAX_PAGE_SIZE),
            ).content
            .map { it.toResponse() }

    private fun NotificationSendLog.toResponse() =
        NotificationSendLogResponse(
            id = id!!,
            source = source,
            type = type,
            segment = segment,
            targetUserId = targetUserId,
            title = title,
            body = body,
            status = status,
            sentCount = sentCount,
            failedCount = failedCount,
            errorMessage = errorMessage,
            broadcastId = broadcastId,
            actorUserId = actorUserId,
            createdAt = createdAt,
        )

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
