package kr.ai.flori.auth.listener

import kr.ai.flori.auth.event.UserRegisteredEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 신규 가입 → Discord 알림(AFTER_COMMIT 비동기). */
@Component
class UserRegisteredEventListener(
    private val discordNotifier: DiscordNotifier,
) {
    // 발송 비동기화는 DiscordNotifier.notify(@Async)가 담당 — 여기 @Async를 두면 이중 디스패치.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: UserRegisteredEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val message =
            """
            **[새 유저 가입 👋]**
            - 가입 일자: $now
            - userId: ${event.userId}
            - 닉네임: ${event.nickname}
            - 소셜: ${event.provider}
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.SIGNUP, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
