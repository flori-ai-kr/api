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
     * @return 성공한 발송 건수.
     */
    fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        url: String? = null,
    ): Int {
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
}
