package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.NotificationSendLogResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.NotificationSendLogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 알림 발송 이력 조회. @RequiresAdmin. */
@RestController
@RequestMapping("/admin/notification-logs")
@RequiresAdmin
class AdminNotificationLogController(
    private val service: NotificationSendLogService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) source: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<NotificationSendLogResponse> = service.list(type, source, status, page, size)
}
