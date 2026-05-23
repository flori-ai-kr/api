package com.hazel.common.error

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 예기치 못한 운영 오류를 Discord 웹훅으로 비동기 전송.
 * - 웹훅 미설정 시 콘솔 로깅으로 폴백(로컬 부팅 가능).
 * - 5분 in-memory 중복 제거. 스택/PII 새니타이즈.
 * - 응답 스레드를 막지 않도록 @Async.
 */
@Component
class DiscordErrorReporter(
    @Value("\${discord.webhook-url:}") private val webhookUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()
    private val recentErrors = ConcurrentHashMap<String, Long>()

    @Async
    fun report(
        throwable: Throwable,
        context: Map<String, String> = emptyMap(),
    ) {
        val message = throwable.message ?: throwable.javaClass.simpleName
        val action = context["action"] ?: "(알 수 없음)"

        if (webhookUrl.isBlank()) {
            log.error("[운영오류] action={} message={}", action, message, throwable)
            return
        }
        if (isDuplicate("$message:$action")) return

        val payload = buildPayload(throwable, message, action)
        runCatching {
            restClient
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        }.onFailure { log.warn("Discord 웹훅 전송 실패: {}", it.message) }
    }

    private fun buildPayload(
        throwable: Throwable,
        message: String,
        action: String,
    ): Map<String, Any> {
        val timestamp = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val fields =
            buildList {
                add(field("오류 메시지", truncate(message, MAX_FIELD_LENGTH)))
                add(field("액션", action))
                add(field("시간 (KST)", timestamp))
                throwable.stackTraceToString().takeIf { it.isNotBlank() }?.let {
                    add(field("스택", "```\n${truncate(sanitizeStack(it), MAX_STACK_LENGTH)}\n```"))
                }
            }
        return mapOf("embeds" to listOf(mapOf("title" to "운영 오류 발생", "color" to EMBED_COLOR, "fields" to fields)))
    }

    private fun field(
        name: String,
        value: String,
    ): Map<String, Any> = mapOf("name" to name, "value" to value, "inline" to false)

    private fun isDuplicate(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = recentErrors.put(key, now)
        if (recentErrors.size > MAX_DEDUP_ENTRIES) {
            recentErrors.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
        }
        return last != null && now - last < DEDUP_WINDOW_MS
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        const val EMBED_COLOR = 0xE5614E
        const val DEDUP_WINDOW_MS = 5 * 60 * 1000L
        const val MAX_DEDUP_ENTRIES = 50
    }
}
