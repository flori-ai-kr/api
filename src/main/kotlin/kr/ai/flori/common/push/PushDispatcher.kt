package kr.ai.flori.common.push

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
) {
    /**
     * 사용자의 모든 활성 구독에 발송한다.
     * type이 주어지고 해당 타입 수신을 꺼두었으면(notification_preferences) 발송하지 않는다(0 반환).
     * type=null(테스트 등)이면 수신설정과 무관하게 항상 발송한다.
     * @return 성공한 발송 건수.
     */
    fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        url: String? = null,
        type: String? = null,
    ): Int {
        if (type != null && !isTypeEnabled(userId, type)) return 0
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
            val gone =
                if (!p256dh.isNullOrBlank() && !auth.isNullOrBlank()) {
                    val result = webPush.send(WebPushTarget(endpoint, p256dh, auth), WebPushPayload(title, body, url))
                    if (result.success) sent++
                    result.subscriptionGone
                } else {
                    val result = fcm.send(PushMessage(token = endpoint, title = title, body = body))
                    if (result.success) sent++
                    result.tokenInvalid
                }
            if (gone) {
                jdbcTemplate.update("UPDATE push_subscriptions SET is_active = FALSE WHERE endpoint = ?", endpoint)
            }
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
}
