package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AdminOverviewResponse
import kr.ai.flori.admin.dto.ChurnReasonSlice
import kr.ai.flori.admin.dto.FunnelStage
import kr.ai.flori.admin.dto.RetentionCohortRow
import kr.ai.flori.admin.dto.TimeseriesPoint
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminStatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/stats")
@RequiresAdmin
class AdminStatsController(
    private val statsService: AdminStatsService,
) {
    @GetMapping("/overview")
    fun overview(
        @RequestParam(defaultValue = "30d") range: String,
    ): AdminOverviewResponse = statsService.overview(range)

    @GetMapping("/timeseries")
    fun timeseries(
        @RequestParam metric: String,
        @RequestParam(defaultValue = "30d") range: String,
    ): List<TimeseriesPoint> = statsService.timeseries(metric, range)

    @GetMapping("/funnel")
    fun funnel(): List<FunnelStage> = statsService.funnel()

    @GetMapping("/churn-reasons")
    fun churnReasons(
        @RequestParam(defaultValue = "30") days: Int,
    ): List<ChurnReasonSlice> = statsService.churnReasons(days)

    @GetMapping("/retention")
    fun retention(): List<RetentionCohortRow> = statsService.retentionCohort()
}
