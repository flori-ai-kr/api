package kr.ai.flori.admin.service

import kr.ai.flori.common.notification.NotificationSendRecorder
import org.springframework.stereotype.Component

/** [NotificationSendRecorder]를 운영 콘솔 발송 로그(notification_send_logs)에 위임하는 어댑터. */
@Component
class NotificationSendLogRecorderAdapter(
    private val logService: NotificationSendLogService,
) : NotificationSendRecorder {
    override fun record(
        source: String,
        type: String,
        success: Boolean,
        targetUserId: Long?,
        title: String?,
        errorMessage: String?,
    ) {
        logService.record(
            source = source,
            type = type,
            sentCount = if (success) 1 else 0,
            failedCount = if (success) 0 else 1,
            title = title,
            targetUserId = targetUserId,
            errorMessage = errorMessage,
        )
    }
}
