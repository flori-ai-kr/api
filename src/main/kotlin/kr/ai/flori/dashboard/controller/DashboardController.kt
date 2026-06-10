package kr.ai.flori.dashboard.controller

import kr.ai.flori.dashboard.dto.MonthDashboardResponse
import kr.ai.flori.dashboard.dto.TodayDashboardResponse
import kr.ai.flori.dashboard.service.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dashboard")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    @GetMapping("/today")
    fun today(): TodayDashboardResponse = dashboardService.today()

    @GetMapping("/month")
    fun month(
        @RequestParam(required = false) month: String?,
    ): MonthDashboardResponse = dashboardService.month(month)
}
