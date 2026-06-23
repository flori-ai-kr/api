package kr.ai.flori.admin.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.dto.BanCreateRequest
import kr.ai.flori.admin.dto.BanResponse
import kr.ai.flori.admin.dto.ReportQueueItem
import kr.ai.flori.admin.dto.ResolveReportRequest
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminModerationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/community")
@RequiresAdmin
class AdminModerationController(
    private val service: AdminModerationService,
) {
    @GetMapping("/reports")
    fun listReports(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<ReportQueueItem> = service.listReports(status, page, size)

    @PostMapping("/reports/{id}/resolve")
    fun resolveReport(
        @PathVariable id: Long,
        @Valid @RequestBody request: ResolveReportRequest,
    ): ReportQueueItem = service.resolveReport(id, request.resolution!!)

    @PostMapping("/posts/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun hidePost(
        @PathVariable id: Long,
    ) {
        service.hidePost(id)
    }

    @PostMapping("/posts/{id}/unhide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unhidePost(
        @PathVariable id: Long,
    ) {
        service.unhidePost(id)
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePost(
        @PathVariable id: Long,
    ) {
        service.deletePost(id)
    }

    @PostMapping("/comments/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun hideComment(
        @PathVariable id: Long,
    ) {
        service.hideComment(id)
    }

    @PostMapping("/comments/{id}/unhide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unhideComment(
        @PathVariable id: Long,
    ) {
        service.unhideComment(id)
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComment(
        @PathVariable id: Long,
    ) {
        service.deleteComment(id)
    }

    @GetMapping("/bans")
    fun listBans(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<BanResponse> = service.listBans(page, size)

    @PostMapping("/bans")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBan(
        @Valid @RequestBody request: BanCreateRequest,
    ): BanResponse = service.createBan(request)

    @DeleteMapping("/bans/{id}")
    fun liftBan(
        @PathVariable id: Long,
    ): BanResponse = service.liftBan(id)
}
