package kr.ai.flori.storage.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.storage.dto.AdminStorageRequestResponse
import kr.ai.flori.storage.dto.ApproveRequest
import kr.ai.flori.storage.dto.RejectRequest
import kr.ai.flori.storage.service.AdminStorageService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/storage")
@RequiresAdmin
class AdminStorageController(
    private val service: AdminStorageService,
) {
    @GetMapping("/requests")
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminStorageRequestResponse> = service.list(status, page, size)

    @PostMapping("/requests/{id}/approve")
    fun approve(
        @PathVariable id: Long,
        @Valid @RequestBody request: ApproveRequest,
    ): AdminStorageRequestResponse = service.approve(id, request.quotaBytes)

    @PostMapping("/requests/{id}/reject")
    fun reject(
        @PathVariable id: Long,
        @Valid @RequestBody request: RejectRequest,
    ): AdminStorageRequestResponse = service.reject(id, request.reason)
}
