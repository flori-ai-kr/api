package kr.ai.flori.billing.service

import kr.ai.flori.billing.dto.RedeemResponse
import kr.ai.flori.billing.entity.Coupon
import kr.ai.flori.billing.entity.CouponRedemption
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.billing.repository.CouponRedemptionRepository
import kr.ai.flori.billing.repository.CouponRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 점주 쿠폰 등록(redeem). 검증4종 → 기록 → 무료일수 적용(또는 pending). */
@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val redemptionRepository: CouponRedemptionRepository,
    private val subscriptionService: SubscriptionService,
) {
    @Transactional
    fun redeem(code: String): RedeemResponse {
        val userId = TenantContext.currentUserId()
        val coupon = couponRepository.findByCode(code) ?: throw AppException(BillingErrorCode.COUPON_NOT_FOUND)
        validateStatus(coupon)
        validatePeriod(coupon)
        validateExhausted(coupon, userId)

        redemptionRepository.save(CouponRedemption(requireNotNull(coupon.id), userId, coupon.days))
        coupon.redeemedCount += 1
        couponRepository.save(coupon)

        val nextBillingAt = subscriptionService.applyFreeDays(userId, coupon.days)
        return RedeemResponse(
            grantedDays = coupon.days,
            nextBillingAt = nextBillingAt,
            pending = nextBillingAt == null,
        )
    }

    private fun validateStatus(coupon: Coupon) {
        if (coupon.status != "ACTIVE") throw AppException(BillingErrorCode.COUPON_DISABLED)
    }

    private fun validatePeriod(coupon: Coupon) {
        val now = Instant.now()
        val notStarted = coupon.validFrom != null && now.isBefore(coupon.validFrom)
        val expired = coupon.validUntil != null && now.isAfter(coupon.validUntil)
        if (notStarted || expired) throw AppException(BillingErrorCode.COUPON_NOT_IN_PERIOD)
    }

    private fun validateExhausted(
        coupon: Coupon,
        userId: Long,
    ) {
        val max = coupon.maxRedemptions
        val globalExhausted = max != null && coupon.redeemedCount >= max
        val userAlreadyUsed = redemptionRepository.existsByCouponIdAndUserId(requireNotNull(coupon.id), userId)
        if (globalExhausted || userAlreadyUsed) throw AppException(BillingErrorCode.COUPON_EXHAUSTED)
    }
}
