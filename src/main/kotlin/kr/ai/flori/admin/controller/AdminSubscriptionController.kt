package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AdminSubscriptionRow
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminSubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/subscriptions")
@RequiresAdmin
class AdminSubscriptionController(
    private val service: AdminSubscriptionService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminSubscriptionRow> = service.list(status, page, size)
}
