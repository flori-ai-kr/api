package kr.ai.flori.billing.service

import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.entity.PaymentHistory
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.support.BillingPeriods
import kr.ai.flori.common.error.AppException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ChargeOutcome { SUCCESS, FAILED, ALREADY_PAID }

/** 단일 결제 단위(스케줄러가 호출). 멱등·기록·성공 시 주기 전진. dunning 상태전이는 스케줄러 책임. */
@Service
class PaymentService(
    private val billingClient: BillingClient,
    private val billingKeyRepository: BillingKeyRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun chargeOnce(subscription: Subscription): ChargeOutcome {
        val subId = requireNotNull(subscription.id)
        val cycle = LocalDate.ofInstant(subscription.nextBillingAt, KST)
        if (paymentHistoryRepository.existsBySubscriptionIdAndBillingCycleAndStatus(subId, cycle, "PAID")) {
            return ChargeOutcome.ALREADY_PAID
        }
        val card =
            billingKeyRepository.findByUserId(subscription.userId)
                ?: throw AppException(kr.ai.flori.billing.error.BillingErrorCode.SUBSCRIPTION_STATE, "등록된 카드가 없습니다")
        val attempt = paymentHistoryRepository.countBySubscriptionIdAndBillingCycle(subId, cycle) + 1
        val orderId = "sub${subId}_${cycle.format(YYYYMMDD)}_a$attempt"

        return try {
            val approved =
                billingClient.approveBilling(
                    billingKey = card.billingKey,
                    customerKey = card.customerKey,
                    amount = subscription.amount,
                    orderId = orderId,
                    orderName = orderName(subscription.plan),
                    idempotencyKey = orderId,
                )
            paymentHistoryRepository.save(
                PaymentHistory(subscription.userId, subId, orderId, cycle, subscription.amount, "PAID").apply {
                    tossPaymentKey = approved.paymentKey
                    approvedAt = parseApprovedAt(approved.approvedAt)
                },
            )
            advance(subscription)
            ChargeOutcome.SUCCESS
        } catch (e: AppException) {
            log.warn("자동결제 실패 subId={} cycle={} code={}", subId, cycle, e.errorCode.code)
            paymentHistoryRepository.save(
                PaymentHistory(subscription.userId, subId, orderId, cycle, subscription.amount, "FAILED").apply {
                    failureCode = e.errorCode.code
                    failureMessage = e.message
                },
            )
            ChargeOutcome.FAILED
        }
    }

    private fun advance(subscription: Subscription) {
        val fresh = subscriptionRepository.findById(requireNotNull(subscription.id)).get()
        val startZdt = fresh.nextBillingAt.atZone(KST)
        val nextZdt = BillingPeriods.next(fresh.plan, startZdt)
        fresh.currentPeriodStart = fresh.nextBillingAt
        fresh.currentPeriodEnd = nextZdt.toInstant()
        fresh.nextBillingAt = nextZdt.toInstant()
        fresh.status = "ACTIVE"
        fresh.retryCount = 0
        fresh.graceUntil = null
        subscriptionRepository.save(fresh)
    }

    private fun parseApprovedAt(value: String?): Instant? =
        value?.let {
            runCatching {
                java.time.OffsetDateTime
                    .parse(it)
                    .toInstant()
            }.getOrNull()
        }

    private fun orderName(plan: String): String = if (plan == "YEARLY") "Flori 연간 구독" else "Flori 월간 구독"

    private companion object {
        val KST = BillingPeriods.KST
        val YYYYMMDD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
