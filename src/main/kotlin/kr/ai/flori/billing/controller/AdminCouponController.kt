package kr.ai.flori.billing.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.billing.dto.CouponDetailResponse
import kr.ai.flori.billing.dto.CouponIssueRequest
import kr.ai.flori.billing.dto.CouponResponse
import kr.ai.flori.billing.service.AdminCouponService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/coupons")
@RequiresAdmin
class AdminCouponController(
    private val adminCouponService: AdminCouponService,
) {
    @GetMapping
    fun list(): List<CouponResponse> = adminCouponService.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(
        @Valid @RequestBody request: CouponIssueRequest,
    ): CouponResponse = adminCouponService.issue(request)

    @GetMapping("/{id}")
    fun detail(
        @PathVariable id: Long,
    ): CouponDetailResponse = adminCouponService.detail(id)

    @PostMapping("/{id}/disable")
    fun disable(
        @PathVariable id: Long,
    ): CouponResponse = adminCouponService.disable(id)
}
