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
        // 코드는 대문자로 통일 저장되므로 입력도 대문자 정규화(대소문자 무관 입력 허용).
        // PESSIMISTIC_WRITE: maxRedemptions 동시 redeem 시 카운트 lost-update 방지.
        val coupon = couponRepository.findByCodeForUpdate(code.trim().uppercase()) ?: throw AppException(BillingErrorCode.COUPON_NOT_FOUND)
        validateStatus(coupon)
        validatePeriod(coupon)
        validateExhausted(coupon, userId)

        val applied = subscriptionService.applyFreeDays(userId, coupon.days)
        redemptionRepository.save(
            CouponRedemption(requireNotNull(coupon.id), userId, coupon.days).apply {
                subscriptionId = applied?.id
            },
        )
        coupon.redeemedCount += 1
        couponRepository.save(coupon)

        return RedeemResponse(
            grantedDays = coupon.days,
            nextBillingAt = applied?.nextBillingAt,
            pending = applied == null,
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
