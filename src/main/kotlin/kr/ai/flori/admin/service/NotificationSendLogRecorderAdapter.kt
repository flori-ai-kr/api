package kr.ai.flori.admin.service

import kr.ai.flori.common.notification.NotificationSendRecorder
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * [NotificationSendRecorder]를 운영 콘솔 발송 로그(notification_send_logs)에 위임하는 어댑터.
 *
 * @Primary: 포트-어댑터 구조상 추후 no-op/테스트용 두 번째 NotificationSendRecorder 빈이 추가돼도
 * 주입 모호성(NoUniqueBeanDefinitionException)을 피하고 운영 어댑터를 기본 주입 대상으로 고정한다.
 */
@Component
@Primary
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
