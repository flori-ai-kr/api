package kr.ai.flori.billing.service

import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushTypes
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 매일 04:30(KST) 결제 3일 전 구독에 사전 알림. 윈도=해당 일자 0~24시(중복발송 방지: 하루 1회 실행이므로 자연 1회). */
@Service
class BillingReminderScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val pushDispatcher: PushDispatcher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    fun scheduledRun() {
        val sent = runReminders(Instant.now())
        log.info("결제 사전알림 발송: {}", sent)
    }

    fun runReminders(now: Instant): Int {
        val from = now.plus(REMIND_DAYS_BEFORE, ChronoUnit.DAYS)
        val to = now.plus(REMIND_DAYS_BEFORE + 1, ChronoUnit.DAYS)
        val targets = subscriptionRepository.findByStatusInAndNextBillingAtBetween(REMIND_STATES, from, to)
        var sent = 0
        targets.forEach { sub ->
            sent += pushDispatcher.sendToUser(sub.userId, "결제 예정 안내", "3일 후 구독료가 결제될 예정이에요.", type = PushTypes.BILLING)
        }
        return sent
    }

    private companion object {
        val REMIND_STATES = setOf("TRIALING", "ACTIVE")
        const val REMIND_DAYS_BEFORE = 3L
    }
}
