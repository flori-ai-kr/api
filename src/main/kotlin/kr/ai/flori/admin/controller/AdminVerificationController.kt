package kr.ai.flori.admin.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.dto.AdminVerificationRejectRequest
import kr.ai.flori.admin.dto.AdminVerificationResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminVerificationService
import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/verifications")
@RequiresAdmin
class AdminVerificationController(
    private val service: AdminVerificationService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "PENDING") status: BusinessVerificationStatuses,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminVerificationResponse> = service.list(status, page, size)

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: Long,
    ): AdminVerificationResponse = service.approve(id)

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Long,
        @Valid @RequestBody request: AdminVerificationRejectRequest,
    ): AdminVerificationResponse = service.reject(id, request.reason)
}
