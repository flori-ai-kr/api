package kr.ai.flori.interview.listener

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.interview.event.InterviewRequestedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 유저 인터뷰 신청 → Discord 알림(AFTER_COMMIT 비동기). */
@Component
class InterviewRequestedEventListener(
    private val discordNotifier: DiscordNotifier,
) {
    // 발송 비동기화는 DiscordNotifier.notify(@Async)가 담당 — 여기 @Async를 두면 이중 디스패치.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: InterviewRequestedEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val message =
            """
            **[새 인터뷰 신청 🎤]**
            - 신청 일자: $now
            - 이름: ${event.name}
            - 연락처: ${event.phone}
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.INTERVIEW, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
