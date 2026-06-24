package kr.ai.flori.billing.repository

import kr.ai.flori.billing.entity.CouponRedemption
import org.springframework.data.jpa.repository.JpaRepository

interface CouponRedemptionRepository : JpaRepository<CouponRedemption, Long> {
    fun existsByCouponIdAndUserId(couponId: Long, userId: Long): Boolean
    fun findByCouponIdOrderByCreatedAtDesc(couponId: Long): List<CouponRedemption>
    fun findByUserIdAndSubscriptionIdIsNull(userId: Long): List<CouponRedemption>
}
