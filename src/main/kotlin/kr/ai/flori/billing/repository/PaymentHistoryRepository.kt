package kr.ai.flori.billing.repository

import kr.ai.flori.billing.entity.PaymentHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface PaymentHistoryRepository : JpaRepository<PaymentHistory, Long> {
    fun existsBySubscriptionIdAndBillingCycleAndStatus(
        subscriptionId: Long,
        billingCycle: LocalDate,
        status: String,
    ): Boolean

    fun countBySubscriptionIdAndBillingCycle(
        subscriptionId: Long,
        billingCycle: LocalDate,
    ): Long

    fun findTop10BySubscriptionIdOrderByCreatedAtDesc(subscriptionId: Long): List<PaymentHistory>

    fun findByOrderId(orderId: String): PaymentHistory?

    fun findByTossPaymentKey(tossPaymentKey: String): PaymentHistory?
}
