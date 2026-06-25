package kr.ai.flori.billing.controller

import jakarta.validation.Valid
import kr.ai.flori.billing.dto.RedeemRequest
import kr.ai.flori.billing.dto.RedeemResponse
import kr.ai.flori.billing.service.CouponService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/coupons")
class CouponController(
    private val couponService: CouponService,
) {
    @PostMapping("/redeem")
    fun redeem(
        @Valid @RequestBody request: RedeemRequest,
    ): RedeemResponse = couponService.redeem(request.code)
}
