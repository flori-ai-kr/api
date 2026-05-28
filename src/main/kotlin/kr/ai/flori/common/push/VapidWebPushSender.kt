package kr.ai.flori.common.push

import com.fasterxml.jackson.databind.ObjectMapper
import nl.martijndwars.webpush.Notification
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import nl.martijndwars.webpush.PushService as VapidPushService

/**
 * VAPID Web Push 발송 구현. 브라우저 구독(endpoint + p256dh + auth)으로 서명된 푸시를 전송한다.
 * 영구 만료(404/410)는 subscriptionGone으로 표시해 호출측이 구독을 비활성화하도록 한다.
 */
class VapidWebPushSender(
    publicKey: String,
    privateKey: String,
    subject: String,
    private val objectMapper: ObjectMapper,
) : WebPushSender {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pushService = VapidPushService(publicKey, privateKey, subject)

    @Suppress("TooGenericExceptionCaught")
    override fun send(
        target: WebPushTarget,
        payload: WebPushPayload,
    ): WebPushResult {
        val body = mutableMapOf<String, Any>("title" to payload.title, "body" to payload.body)
        payload.url?.let { body["url"] = it }
        val payloadBytes = objectMapper.writeValueAsString(body).toByteArray(StandardCharsets.UTF_8)
        return try {
            val notification = Notification(target.endpoint, target.p256dh, target.auth, payloadBytes)
            val status = pushService.send(notification).statusLine.statusCode
            val gone = status == HTTP_NOT_FOUND || status == HTTP_GONE
            WebPushResult(success = status in SUCCESS_RANGE, subscriptionGone = gone)
        } catch (e: Exception) {
            log.warn("웹푸시 전송 실패 endpoint={}...", target.endpoint.take(ENDPOINT_PREVIEW), e)
            WebPushResult(success = false)
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
        const val ENDPOINT_PREVIEW = 32
        val SUCCESS_RANGE = 200..299
    }
}
