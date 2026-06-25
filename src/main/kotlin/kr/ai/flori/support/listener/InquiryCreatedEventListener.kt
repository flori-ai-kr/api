package kr.ai.flori.support.listener

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.support.event.InquiryCreatedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Component
class InquiryCreatedEventListener(
    private val discordNotifier: DiscordNotifier,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: InquiryCreatedEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val message =
            """
            **[📬 새 문의 접수]**
            - 카테고리: ${event.category}
            - 제목: `${event.title.take(TITLE_MAX_DISPLAY)}`
            - 작성자: user_${event.userId}
            - 접수 시각: $now
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.SUPPORT, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        const val TITLE_MAX_DISPLAY = 200
    }
}
