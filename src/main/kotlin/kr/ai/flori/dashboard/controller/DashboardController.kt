package kr.ai.flori.dashboard.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.ai.flori.dashboard.dto.MonthDashboardResponse
import kr.ai.flori.dashboard.dto.TodayDashboardResponse
import kr.ai.flori.dashboard.service.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Dashboard", description = "대시보드 · 통계")
@RestController
@RequestMapping("/dashboard")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    @Operation(summary = "오늘 대시보드", description = "오늘 매출 요약 + 다가오는 예약 + 발동 리마인더 + 최근 매출 + 카테고리")
    @GetMapping("/today")
    fun today(): TodayDashboardResponse = dashboardService.today()

    @Operation(summary = "월 통계", description = "월 매출/지출 요약 + 카테고리/결제수단/채널/고객/지출 통계(네이티브 SQL 집계)")
    @GetMapping("/month")
    fun month(
        @RequestParam(required = false) month: String?,
    ): MonthDashboardResponse = dashboardService.month(month)
}
