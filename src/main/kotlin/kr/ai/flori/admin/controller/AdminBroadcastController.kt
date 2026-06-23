package kr.ai.flori.admin.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.dto.BroadcastCreateRequest
import kr.ai.flori.admin.dto.BroadcastResponse
import kr.ai.flori.admin.dto.SegmentPreviewResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminBroadcastService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/broadcasts")
@RequiresAdmin
class AdminBroadcastController(
    private val service: AdminBroadcastService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<BroadcastResponse> = service.list(status, page, size)

    @PostMapping
    fun create(
        @Valid @RequestBody request: BroadcastCreateRequest,
    ): BroadcastResponse = service.create(request)

    @GetMapping("/segments/preview")
    fun previewSegment(
        @RequestParam segment: String,
    ): SegmentPreviewResponse = service.previewSegment(segment)

    @PostMapping("/{id}/send")
    fun send(
        @PathVariable id: Long,
    ): BroadcastResponse = service.send(id)

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ) = service.delete(id)
}
