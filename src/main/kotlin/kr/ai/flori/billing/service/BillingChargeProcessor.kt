package kr.ai.flori.billing.service

import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.event.PaymentChargedEvent
import kr.ai.flori.billing.event.SubscriptionExpiredEvent
import kr.ai.flori.billing.repository.SubscriptionRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 정기결제 건별 처리(트랜잭션 격리).
 * RecurringBillingScheduler 루프가 이 빈을 주입받아 호출 → self-invocation 없이 @Transactional 적용.
 */
@Service
class BillingChargeProcessor(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentService: PaymentService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun process(
        subscription: Subscription,
        now: Instant,
    ) {
        val subId = requireNotNull(subscription.id)
        if (subscription.cancelAtPeriodEnd) {
            expire(subscription, "해지 예약")
            return
        }
        when (paymentService.chargeOnce(subscription)) {
            ChargeOutcome.SUCCESS, ChargeOutcome.ALREADY_PAID -> {
                eventPublisher.publishEvent(
                    PaymentChargedEvent(subscription.userId, subId, subscription.amount, success = true),
                )
            }
            ChargeOutcome.FAILED -> applyDunning(subscription, now)
        }
    }

    private fun applyDunning(
        subscription: Subscription,
        now: Instant,
    ) {
        val subId = requireNotNull(subscription.id)
        val fresh = subscriptionRepository.findById(subId).orElse(subscription)
        if (fresh.status != "IN_GRACE") {
            fresh.status = "IN_GRACE"
            fresh.graceUntil =
                LocalDate
                    .now(KST)
                    .plusDays(GRACE_DAYS)
                    .atStartOfDay(KST)
                    .toInstant()
        }
        fresh.retryCount += 1
        val graceUntil = fresh.graceUntil
        if (graceUntil != null && !now.isBefore(graceUntil)) {
            fresh.status = "EXPIRED"
            subscriptionRepository.save(fresh)
            eventPublisher.publishEvent(SubscriptionExpiredEvent(fresh.userId, subId, "연체 만료"))
            eventPublisher.publishEvent(PaymentChargedEvent(fresh.userId, subId, fresh.amount, success = false))
            return
        }
        subscriptionRepository.save(fresh)
        eventPublisher.publishEvent(PaymentChargedEvent(fresh.userId, subId, fresh.amount, success = false))
    }

    private fun expire(
        subscription: Subscription,
        reason: String,
    ) {
        subscription.status = "EXPIRED"
        subscriptionRepository.save(subscription)
        eventPublisher.publishEvent(
            SubscriptionExpiredEvent(subscription.userId, requireNotNull(subscription.id), reason),
        )
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val GRACE_DAYS = 3L
    }
}
