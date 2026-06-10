package kr.ai.flori.common.push

import org.slf4j.LoggerFactory

/**
 * FCM 미구성 환경(로컬/테스트)용 폴백. 실제 전송 대신 로깅만 한다.
 */
class LoggingPushService : PushService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(message: PushMessage): PushResult {
        log.info("[푸시-로깅] token={}... title={}", message.token.take(TOKEN_PREVIEW), message.title)
        return PushResult(success = true)
    }

    private companion object {
        const val TOKEN_PREVIEW = 8
    }
}
