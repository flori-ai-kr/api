package kr.ai.flori.common.notification

/**
 * 발송 결과 기록 포트. common(예: SolapiNotifier)이 admin의 발송 로그에 직접 의존하지 않도록
 * 인터페이스만 common에 두고, 구현(어댑터)은 admin에 둔다(의존성 방향 보호).
 */
interface NotificationSendRecorder {
    fun record(
        source: String,
        type: String,
        success: Boolean,
        targetUserId: Long?,
        title: String?,
        errorMessage: String?,
    )
}
