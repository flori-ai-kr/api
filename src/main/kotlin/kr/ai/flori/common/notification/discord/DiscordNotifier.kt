package kr.ai.flori.common.notification.discord

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Discord 알림 전송 원시 도구(재사용). 채널 → 웹훅 URL 해석 후 POST.
 * - @Async: 응답/리스너 스레드 비차단.
 * - URL 공백이면 콘솔 로깅 폴백(로컬·테스트 부팅 가능).
 * - best-effort: 전송 실패는 로깅만(본 작업을 막지 않음).
 */
@Component
class DiscordNotifier(
    private val properties: DiscordProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    @Async
    @Suppress("TooGenericExceptionCaught")
    fun notify(
        channel: DiscordChannel,
        message: DiscordMessage,
    ) {
        val url = channel.urlSelector(properties)
        if (url.isBlank()) {
            log.info("[Discord:{}] 웹훅 미설정 — 콘솔 폴백: {}", channel, message.content)
            return
        }
        try {
            restClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("[Discord:{}] 웹훅 전송 실패(무시): {}", channel, e.message)
        }
    }
}
