package kr.ai.flori.waitlist.listener

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.waitlist.event.WaitlistRegisteredEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 사전등록 완료 → Discord 알림(AFTER_COMMIT 비동기). */
@Component
class WaitlistRegisteredEventListener(
    private val discordNotifier: DiscordNotifier,
) {
    // 발송 비동기화는 DiscordNotifier.notify(@Async)가 담당 — 여기 @Async를 두면 이중 디스패치.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: WaitlistRegisteredEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val message =
            """
            **[새 사전등록 🎁]**
            - 등록 일자: $now
            - 이메일: ${event.email}
            - 가게명: ${event.shopName}
            - 누적: ${event.count} / ${event.capacity}명
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.WAITLIST, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
