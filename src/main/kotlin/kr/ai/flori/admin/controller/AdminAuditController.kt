package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AdminAuditLogResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminAuditService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 운영자 감사 로그 조회. @RequiresAdmin. */
@RestController
@RequestMapping("/admin/audit-logs")
@RequiresAdmin
class AdminAuditController(
    private val service: AdminAuditService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) actorUserId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminAuditLogResponse> = service.list(action, actorUserId, page, size)
}
