package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AdminOverviewResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminStatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/stats")
@RequiresAdmin
class AdminStatsController(
    private val statsService: AdminStatsService,
) {
    @GetMapping("/overview")
    fun overview(): AdminOverviewResponse = statsService.overview()
}
