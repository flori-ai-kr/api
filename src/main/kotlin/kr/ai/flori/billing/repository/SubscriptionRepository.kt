package kr.ai.flori.billing.repository

import kr.ai.flori.billing.entity.Subscription
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    fun findByUserId(userId: Long): Subscription?

    // 결제일 도래분(스케줄러). status 목록은 호출부가 지정.
    fun findByStatusInAndNextBillingAtLessThanEqual(
        statuses: Collection<String>,
        at: Instant,
    ): List<Subscription>

    // D-3 사전알림 대상.
    fun findByStatusInAndNextBillingAtBetween(
        statuses: Collection<String>,
        from: Instant,
        to: Instant,
    ): List<Subscription>
}
