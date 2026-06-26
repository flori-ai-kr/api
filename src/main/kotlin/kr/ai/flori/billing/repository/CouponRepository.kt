package kr.ai.flori.billing.repository

import jakarta.persistence.LockModeType
import kr.ai.flori.billing.entity.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CouponRepository : JpaRepository<Coupon, Long> {
    // redeem 경로 전용: maxRedemptions 동시성 보호를 위해 비관적 쓰기 락 사용.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.code = :code")
    fun findByCodeForUpdate(code: String): Coupon?

    fun findByCode(code: String): Coupon?

    fun existsByCode(code: String): Boolean
}
