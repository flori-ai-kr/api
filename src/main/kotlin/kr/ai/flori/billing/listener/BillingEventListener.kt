package kr.ai.flori.billing.listener

import kr.ai.flori.billing.event.SubscriptionStartedEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.push.PushDispatcher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 빌링 도메인 이벤트 → 디스코드 + 유저 푸시(비동기는 DiscordNotifier/@Async가 담당). */
@Component
class BillingEventListener(
    private val discordNotifier: DiscordNotifier,
    private val pushDispatcher: PushDispatcher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubscriptionStarted(event: SubscriptionStartedEvent) {
        val kind = if (event.trial) "체험 시작" else "구독 시작"
        discordNotifier.notify(
            DiscordChannel.BILLING,
            DiscordMessage.of(
                "**[$kind]** userId=${event.userId} plan=${event.plan} ₩${event.amount}",
            ),
        )
        val body = if (event.trial) "14일 무료체험이 시작됐어요." else "구독이 시작됐어요."
        pushDispatcher.sendToUser(event.userId, "Flori 구독", body)
    }
}
