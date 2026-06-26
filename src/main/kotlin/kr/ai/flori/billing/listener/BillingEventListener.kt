package kr.ai.flori.billing.listener

import kr.ai.flori.billing.event.PaymentChargedEvent
import kr.ai.flori.billing.event.SubscriptionExpiredEvent
import kr.ai.flori.billing.event.SubscriptionStartedEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushTypes
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
        pushDispatcher.sendToUser(event.userId, "Flori 구독", body, type = PushTypes.BILLING)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCharged(event: PaymentChargedEvent) {
        if (event.success) {
            discordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of("**[결제 성공]** userId=${event.userId} ₩${event.amount}"))
        } else {
            discordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of("**[결제 실패]** userId=${event.userId} ₩${event.amount}"))
            pushDispatcher.sendToUser(event.userId, "결제 실패", "결제가 실패했어요. 카드를 확인해 주세요.", type = PushTypes.BILLING)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubscriptionExpired(event: SubscriptionExpiredEvent) {
        discordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of("**[구독 만료]** userId=${event.userId} 사유=${event.reason}"))
        pushDispatcher.sendToUser(event.userId, "구독 만료", "구독이 만료됐어요.", type = PushTypes.BILLING)
    }
}
