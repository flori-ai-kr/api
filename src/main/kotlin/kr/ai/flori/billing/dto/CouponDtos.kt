package kr.ai.flori.billing.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class RedeemRequest(
    @field:NotBlank(message = "쿠폰 코드는 필수입니다")
    val code: String,
)

data class RedeemResponse(
    val grantedDays: Int,
    val nextBillingAt: Instant?,
    val pending: Boolean,
)

data class CouponIssueRequest(
    val code: String? = null,
    @field:jakarta.validation.constraints.Min(1, message = "무료 일수는 1 이상")
    val days: Int,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
    val maxRedemptions: Int? = null,
    val perUserLimit: Int = 1,
    val source: String = "PROMO",
    val memo: String? = null,
)

data class RedemptionRow(
    val userId: Long,
    val grantedDays: Int,
    val redeemedAt: java.time.Instant,
    // 콘솔 사용현황 표시용 유저 식별자(닉네임=users.nickname / 가게명=user_profiles.store_name). 매핑 없으면 null.
    val nickname: String? = null,
    val storeName: String? = null,
)

data class CouponUpdateRequest(
    @field:jakarta.validation.constraints.Min(1, message = "무료 일수는 1 이상")
    val days: Int,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
    val maxRedemptions: Int? = null,
    val perUserLimit: Int = 1,
    val memo: String? = null,
)

data class CouponDetailResponse(
    val coupon: CouponResponse,
    val redemptions: List<RedemptionRow>,
)

data class CouponResponse(
    val id: Long,
    val code: String,
    val days: Int,
    val status: String,
    val effectiveStatus: String,
    val redeemedCount: Int,
    val maxRedemptions: Int?,
    val perUserLimit: Int,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val source: String,
    val memo: String?,
    val createdAt: Instant,
) {
    companion object {
        fun of(
            c: kr.ai.flori.billing.entity.Coupon,
            now: Instant,
        ): CouponResponse =
            CouponResponse(
                id = requireNotNull(c.id),
                code = c.code,
                days = c.days,
                status = c.status,
                effectiveStatus = effective(c, now),
                redeemedCount = c.redeemedCount,
                maxRedemptions = c.maxRedemptions,
                perUserLimit = c.perUserLimit,
                validFrom = c.validFrom,
                validUntil = c.validUntil,
                source = c.source,
                memo = c.memo,
                createdAt = c.createdAt,
            )

        private fun effective(
            c: kr.ai.flori.billing.entity.Coupon,
            now: Instant,
        ): String =
            when {
                c.status == "DISABLED" -> "DISABLED"
                c.validUntil != null && now.isAfter(c.validUntil) -> "EXPIRED"
                c.maxRedemptions != null && c.redeemedCount >= c.maxRedemptions!! -> "EXHAUSTED"
                else -> "ACTIVE"
            }
    }
}
