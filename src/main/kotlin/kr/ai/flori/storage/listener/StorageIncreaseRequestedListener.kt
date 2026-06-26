package kr.ai.flori.storage.listener

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.storage.event.StorageIncreaseRequestedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 스토리지 증설 요청 → 운영자 Discord 알림(SUPPORT 채널). DB 커밋 후(AFTER_COMMIT) 발송.
 */
@Component
class StorageIncreaseRequestedListener(
    private val discordNotifier: DiscordNotifier,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: StorageIncreaseRequestedEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val usedGb = "%.2f".format(event.usedBytes / GIB)
        val quotaGb = "%.2f".format(event.quotaBytes / GIB)
        val author = event.nickname ?: "user_${event.userId}"
        val message =
            """
            **[💾 스토리지 증설 요청]**
            - 가게명: ${event.storeName ?: "-"}
            - 작성자: $author (userId: ${event.userId})
            - 현재 사용: ${usedGb}GB / ${quotaGb}GB
            - 사유: ${event.reason ?: "-"}
            - 요청 시각: $now
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.SUPPORT, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        const val GIB = 1024.0 * 1024 * 1024
    }
}
