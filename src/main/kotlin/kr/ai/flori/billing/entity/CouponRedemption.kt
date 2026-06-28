package kr.ai.flori.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/** 쿠폰 사용 기록. (coupon_id, user_id) UNIQUE. createdAt = 사용 시각. */
@Entity
@Table(name = "coupon_redemption")
class CouponRedemption(
    @Column(name = "coupon_id", nullable = false)
    var couponId: Long,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "granted_days", nullable = false)
    var grantedDays: Int,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "subscription_id")
    var subscriptionId: Long? = null
}
