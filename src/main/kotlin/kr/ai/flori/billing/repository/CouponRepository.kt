package kr.ai.flori.billing.repository

import kr.ai.flori.billing.entity.Coupon
import org.springframework.data.jpa.repository.JpaRepository

interface CouponRepository : JpaRepository<Coupon, Long> {
    fun findByCode(code: String): Coupon?

    fun existsByCode(code: String): Boolean
}
