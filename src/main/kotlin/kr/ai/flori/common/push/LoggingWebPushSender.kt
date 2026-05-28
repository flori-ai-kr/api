package kr.ai.flori.common.push

import org.slf4j.LoggerFactory

/**
 * VAPID 키 미설정 환경(로컬/테스트)용 폴백. 실제 전송 대신 로깅만 한다.
 */
class LoggingWebPushSender : WebPushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        target: WebPushTarget,
        payload: WebPushPayload,
    ): WebPushResult {
        log.info("[웹푸시-로깅] endpoint={}... title={}", target.endpoint.take(ENDPOINT_PREVIEW), payload.title)
        return WebPushResult(success = true)
    }

    private companion object {
        const val ENDPOINT_PREVIEW = 32
    }
}
