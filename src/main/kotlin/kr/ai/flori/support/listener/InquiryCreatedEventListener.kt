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
        val category = CATEGORY_LABELS[event.category] ?: event.category
        val author = event.nickname ?: "user_${event.userId}"
        val message =
            """
            **[📬 새 문의 접수]**
            - 카테고리: $category
            - 가게명: ${event.storeName ?: "-"}
            - 작성자: $author
            - 제목: `${event.title.take(TITLE_MAX_DISPLAY)}`
            - 접수 시각: $now
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.SUPPORT, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        const val TITLE_MAX_DISPLAY = 200

        /** 문의 카테고리 코드 → 디스코드 표시용 한글 라벨. */
        val CATEGORY_LABELS =
            mapOf(
                "bug" to "버그",
                "feature" to "기능제안",
                "account" to "계정",
                "payment" to "결제",
                "feedback" to "피드백",
                "etc" to "기타",
            )
    }
}
