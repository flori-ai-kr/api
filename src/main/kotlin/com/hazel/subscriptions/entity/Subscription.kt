package com.hazel.subscriptions.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 사용자별 현재 구독 상태(사용자당 1행). 서버가 SSOT.
 * 멀티테넌시: 모든 조회/변경은 user_id로 격리한다(서비스에서 강제).
 *
 * status: active | in_grace | expired | none — RevenueCat 웹훅 이벤트로 갱신.
 */
@Entity
@Table(name = "subscriptions")
class Subscription(
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: UUID,
    @Column(name = "store", nullable = false)
    var store: String,
    @Column(name = "product_id", nullable = false)
    var productId: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "entitlement", nullable = false)
    var entitlement: String = "premium"

    @Column(name = "status", nullable = false)
    var status: String = "none"

    @Column(name = "original_transaction_id")
    var originalTransactionId: String? = null

    @Column(name = "current_period_end")
    var currentPeriodEnd: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
