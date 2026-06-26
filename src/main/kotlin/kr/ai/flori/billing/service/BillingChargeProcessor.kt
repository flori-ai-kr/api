package kr.ai.flori.billing.service

import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.event.PaymentChargedEvent
import kr.ai.flori.billing.event.SubscriptionExpiredEvent
import kr.ai.flori.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(
        subscription: Subscription,
        now: Instant,
    ) {
        val subId = requireNotNull(subscription.id)
        // 무카드 체험: 카드(빌링키) 없으면 토스 호출 없이 만료. 카드 등록은 결제벽(subscribe)에서만.
        if (subscription.billingKeyId == null) {
            log.info("무카드 체험 만료 처리 subId={}", subId)
            expire(subscription, "무카드 체험 만료")
            return
        }
        if (subscription.cancelAtPeriodEnd) {
            expire(subscription, "해지 예약")
            return
        }
        when (paymentService.chargeOnce(subscription)) {
            ChargeOutcome.SUCCESS -> {
                eventPublisher.publishEvent(
                    PaymentChargedEvent(subscription.userId, subId, subscription.amount, success = true),
                )
            }
            ChargeOutcome.ALREADY_PAID -> {
                // 멱등 재시도 — 이미 결제·알림 완료, 중복 알림 방지
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
        // 만료를 유발한 마지막 실패도 누적(실패 시도 카운트)
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
