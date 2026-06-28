package kr.ai.flori.billing.service

import kr.ai.flori.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

/** 매일 04:00(KST) 결제일 도래 구독 처리. 건별 격리(한 건 실패가 나머지를 막지 않음). */
@Service
class RecurringBillingScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val chargeProcessor: BillingChargeProcessor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun scheduledRun() {
        val count = runDueCharges(Instant.now())
        log.info("정기결제 처리 완료: processed={}", count)
    }

    fun runDueCharges(now: Instant): Int {
        val due = subscriptionRepository.findByStatusInAndNextBillingAtLessThanEqual(DUE_STATES, now)
        var processed = 0
        due.forEach { sub ->
            runCatching { chargeProcessor.process(sub, now) }
                .onFailure { log.error("정기결제 건 처리 실패 subId={}", sub.id, it) }
            processed++
        }
        return processed
    }

    private companion object {
        val DUE_STATES = setOf("TRIALING", "ACTIVE", "IN_GRACE")
    }
}
