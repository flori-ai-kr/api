package kr.ai.flori.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import java.time.Instant
import java.time.LocalDate

/** 결제 시도 장부(append-only). 한 주기당 PAID 1건(부분 유니크). */
@Entity
@Table(name = "payment_history")
class PaymentHistory(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Long,
    @Column(name = "order_id", nullable = false)
    var orderId: String,
    @Column(name = "billing_cycle", nullable = false)
    var billingCycle: LocalDate,
    @Column(name = "amount", nullable = false)
    var amount: Int,
    @Column(name = "status", nullable = false)
    var status: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "toss_payment_key")
    var tossPaymentKey: String? = null

    @Column(name = "failure_code")
    var failureCode: String? = null

    @Column(name = "failure_message")
    var failureMessage: String? = null

    @Column(name = "approved_at")
    var approvedAt: Instant? = null
}
