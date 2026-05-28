package kr.ai.flori.common.push

/**
 * Web Push(VAPID) 발송 추상화. 브라우저(PWA) 구독(endpoint + p256dh + auth)으로 전송한다.
 * FCM([PushService])과 별개 전송 경로 — 모바일은 FCM, 웹은 VAPID.
 */
interface WebPushSender {
    fun send(
        target: WebPushTarget,
        payload: WebPushPayload,
    ): WebPushResult
}

data class WebPushTarget(
    val endpoint: String,
    val p256dh: String,
    val auth: String,
)

/** 브라우저 service worker(sw.js)가 파싱하는 페이로드 형태({title, body, url}). */
data class WebPushPayload(
    val title: String,
    val body: String,
    val url: String? = null,
)

/**
 * @param subscriptionGone 구독 영구 만료(404/410) — 호출측이 구독을 비활성화해야 함.
 */
data class WebPushResult(
    val success: Boolean,
    val subscriptionGone: Boolean = false,
)
