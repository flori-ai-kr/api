package kr.ai.flori.billing.listener

import kr.ai.flori.billing.event.PaymentChargedEvent
import kr.ai.flori.billing.event.SubscriptionExpiredEvent
import kr.ai.flori.billing.event.SubscriptionStartedEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 빌링 도메인 이벤트 → 디스코드 운영 알림(운영자 가시성).
 * 정책: 구독·체험 관련 유저 푸시 알림은 보내지 않는다(요청에 따라 전부 제거).
 */
@Component
class BillingEventListener(
    private val discordNotifier: DiscordNotifier,
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
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCharged(event: PaymentChargedEvent) {
        val label = if (event.success) "결제 성공" else "결제 실패"
        discordNotifier.notify(
            DiscordChannel.BILLING,
            DiscordMessage.of("**[$label]** userId=${event.userId} ₩${event.amount}"),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubscriptionExpired(event: SubscriptionExpiredEvent) {
        discordNotifier.notify(
            DiscordChannel.BILLING,
            DiscordMessage.of("**[구독 만료]** userId=${event.userId} 사유=${event.reason}"),
        )
    }
}
