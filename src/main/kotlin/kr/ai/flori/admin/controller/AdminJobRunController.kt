package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.JobRunLogResponse
import kr.ai.flori.admin.dto.JobRunSummaryResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminJobRunService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 백그라운드 작업(cron) 실행 로그 조회 + 수동 트리거. @RequiresAdmin. */
@RestController
@RequestMapping("/admin/job-runs")
@RequiresAdmin
class AdminJobRunController(
    private val service: AdminJobRunService,
) {
    /** 작업별 최신 상태(콘솔 상단 카드). */
    @GetMapping("/summary")
    fun summary(): List<JobRunSummaryResponse> = service.summary()

    /** 실행 이력 목록(콘솔 하단 테이블). */
    @GetMapping
    fun list(
        @RequestParam(required = false) jobName: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<JobRunLogResponse> = service.list(jobName, status, page, size)

    /** 작업 즉시 실행(수동 트리거). 실행 직후 최신 상태 반환. */
    @PostMapping("/{jobName}/trigger")
    fun trigger(
        @PathVariable jobName: String,
    ): JobRunSummaryResponse = service.trigger(jobName)
}
