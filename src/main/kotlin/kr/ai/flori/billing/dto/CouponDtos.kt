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
