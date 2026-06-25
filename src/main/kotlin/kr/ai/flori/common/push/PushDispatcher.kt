package kr.ai.flori.common.push

import kr.ai.flori.common.notification.NotificationSendRecorder
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 사용자 푸시 발송 라우터. push_subscriptions의 활성 구독을 전송 경로별로 분기한다:
 * - p256dh/auth가 있으면 Web Push(VAPID, 브라우저)
 * - 없으면 FCM(모바일 토큰)
 *
 * 영구 실패(웹푸시 404/410, FCM UNREGISTERED/INVALID)는 구독을 비활성화한다.
 * 건별 독립 처리 — 한 구독 실패가 나머지를 막지 않는다.
 */
@Component
class PushDispatcher(
    private val fcm: PushService,
    private val webPush: WebPushSender,
    private val jdbcTemplate: JdbcTemplate,
    private val sendRecorder: NotificationSendRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자의 모든 활성 구독에 발송한다.
     * type이 토글 가능(TOGGLEABLE)인데 해당 타입 수신을 꺼뒀으면 발송하지 않는다(0 반환).
     * 강제 타입(공지·테스트 등 비-TOGGLEABLE)은 수신설정과 무관하게 항상 발송한다.
     * @return 성공한 발송 건수.
     */
    fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        link: PushLink? = null,
        type: String,
    ): Int {
        if (!isTypeEnabled(userId, type)) return 0
        val rows =
            jdbcTemplate.queryForList(
                "SELECT endpoint, p256dh, auth FROM push_subscriptions WHERE user_id = ?::bigint AND is_active = TRUE",
                userId,
            )
        var sent = 0
        rows.forEach { row ->
            val endpoint = row["endpoint"] as String
            val p256dh = row["p256dh"] as String?
            val auth = row["auth"] as String?
            val isWeb = !p256dh.isNullOrBlank() && !auth.isNullOrBlank()
            val (success, gone) =
                if (isWeb) {
                    // web(sw.js): webUrl 을 그대로 navigate
                    val result = webPush.send(WebPushTarget(endpoint, p256dh, auth), WebPushPayload(title, body, link?.webUrl))
                    result.success to result.subscriptionGone
                } else {
                    // mobile(FCM): resolveNotificationRoute 가 읽는 type/id 를 data 로 전달
                    val result =
                        fcm.send(PushMessage(token = endpoint, title = title, body = body, data = link?.toData() ?: emptyMap()))
                    result.success to result.tokenInvalid
                }
            if (success) sent++
            if (gone) {
                jdbcTemplate.update("UPDATE push_subscriptions SET is_active = FALSE WHERE endpoint = ?", endpoint)
            }
            recordSend(userId, type, isWeb, success, gone, title)
        }
        return sent
    }

    /** 토글 가능 타입만 수신설정을 확인한다(행 없으면 기본 켜짐). 강제 타입은 항상 true. */
    private fun isTypeEnabled(
        userId: Long,
        type: String,
    ): Boolean {
        if (type !in PushTypes.TOGGLEABLE) return true
        val enabled =
            jdbcTemplate.queryForList(
                "SELECT enabled FROM notification_preferences WHERE user_id = ?::bigint AND type = ?",
                Boolean::class.java,
                userId,
                type,
            )
        return enabled.firstOrNull() ?: true
    }

    /** 발송 1건을 운영 콘솔 발송 로그(notification_send_logs)에 기록. 비차단·best-effort. */
    private fun recordSend(
        userId: Long,
        type: String,
        isWeb: Boolean,
        success: Boolean,
        gone: Boolean,
        title: String,
    ) {
        // 브로드캐스트는 AdminBroadcastService 가 세그먼트 단위 집계행을 따로 남긴다(중복 방지).
        if (type == PushTypes.BROADCAST) return
        runCatching {
            sendRecorder.record(
                source = if (isWeb) SOURCE_WEB_PUSH else SOURCE_FCM,
                type = type,
                success = success,
                targetUserId = userId,
                title = title,
                errorMessage =
                    when {
                        success -> null
                        gone -> "구독 만료"
                        else -> "발송 실패"
                    },
            )
        }.onFailure { log.warn("발송 로그 기록 실패 userId={} type={}", userId, type, it) }
    }

    private companion object {
        const val SOURCE_WEB_PUSH = "web_push"
        const val SOURCE_FCM = "fcm"
    }
}
