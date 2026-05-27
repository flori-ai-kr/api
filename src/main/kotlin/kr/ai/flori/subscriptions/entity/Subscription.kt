package kr.ai.flori.subscriptions.entity

import jakarta.persistence.*
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

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
    var userId: Long,
    @Column(name = "store", nullable = false)
    var store: String,
    @Column(name = "product_id", nullable = false)
    var productId: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "entitlement", nullable = false)
    var entitlement: String = "premium"

    @Column(name = "status", nullable = false)
    var status: String = "none"

    @Column(name = "original_transaction_id")
    var originalTransactionId: String? = null

    @Column(name = "current_period_end")
    var currentPeriodEnd: Instant? = null
}
