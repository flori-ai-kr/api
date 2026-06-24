package kr.ai.flori.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/** 유저당 구독 1건. status ∈ TRIALING/ACTIVE/IN_GRACE/EXPIRED. */
@Entity
@Table(name = "subscription")
class Subscription(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "plan", nullable = false)
    var plan: String,
    @Column(name = "status", nullable = false)
    var status: String,
    @Column(name = "amount", nullable = false)
    var amount: Int,
    @Column(name = "next_billing_at", nullable = false)
    var nextBillingAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "billing_key_id")
    var billingKeyId: Long? = null

    @Column(name = "current_period_start")
    var currentPeriodStart: Instant? = null

    @Column(name = "current_period_end")
    var currentPeriodEnd: Instant? = null

    @Column(name = "cancel_at_period_end", nullable = false)
    var cancelAtPeriodEnd: Boolean = false

    @Column(name = "grace_until")
    var graceUntil: Instant? = null

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0
}
