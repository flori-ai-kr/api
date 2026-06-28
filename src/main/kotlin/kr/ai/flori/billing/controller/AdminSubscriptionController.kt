package kr.ai.flori.billing.controller

import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.billing.dto.AdminSubscriptionRow
import kr.ai.flori.billing.service.AdminSubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/subscriptions")
@RequiresAdmin
class AdminSubscriptionController(
    private val adminSubscriptionService: AdminSubscriptionService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminSubscriptionRow> = adminSubscriptionService.list(status, page, size)
}
