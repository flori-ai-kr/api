package kr.ai.flori.billing.service

import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.dto.TossWebhookEvent
import kr.ai.flori.billing.entity.PaymentHistory
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 토스 결제 웹훅 처리. 본문 비신뢰 — paymentKey로 재조회해 권위 상태로 동기화. */
@Service
class TossWebhookService(
    private val billingClient: BillingClient,
    private val paymentHistoryRepository: PaymentHistoryRepository,
    private val discordNotifier: DiscordNotifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(event: TossWebhookEvent) {
        findCancelTarget(event)?.let { history ->
            history.status = "CANCELED"
            paymentHistoryRepository.save(history)
            discordNotifier.notify(
                DiscordChannel.BILLING,
                DiscordMessage.of("**[환불/취소]** userId=${history.userId} ₩${history.amount} order=${history.orderId}"),
            )
        }
    }

    /**
     * 이벤트 타입이 처리 대상이고 재조회 상태가 취소이면 동기화할 [PaymentHistory] 반환.
     * 무관 이벤트/조회 실패/미매칭은 null(조용한 무시 — 항상 200).
     */
    private fun findCancelTarget(event: TossWebhookEvent): PaymentHistory? {
        if (event.eventType !in HANDLED_EVENTS) return null
        return event.data?.paymentKey?.let { key -> fetchCanceledHistory(key) }
    }

    private fun fetchCanceledHistory(paymentKey: String): PaymentHistory? {
        val status =
            runCatching { billingClient.getPayment(paymentKey).status }
                .getOrElse {
                    log.warn("웹훅 재조회 실패 paymentKey={}", paymentKey)
                    return null
                }
        if (status !in CANCELED_STATES) return null
        return paymentHistoryRepository
            .findByTossPaymentKey(paymentKey)
            ?.takeIf { it.status != "CANCELED" }
    }

    private companion object {
        val HANDLED_EVENTS = setOf("PAYMENT_STATUS_CHANGED", "CANCEL_STATUS_CHANGED")
        val CANCELED_STATES = setOf("CANCELED", "PARTIAL_CANCELED")
    }
}
