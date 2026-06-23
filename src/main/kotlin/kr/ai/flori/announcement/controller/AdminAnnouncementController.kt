package kr.ai.flori.announcement.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.announcement.dto.AnnouncementCreateRequest
import kr.ai.flori.announcement.dto.AnnouncementResponse
import kr.ai.flori.announcement.dto.AnnouncementSetActiveRequest
import kr.ai.flori.announcement.dto.AnnouncementUpdateRequest
import kr.ai.flori.announcement.service.AnnouncementService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 공지 배너 CMS(운영 콘솔). cross-tenant — @RequiresAdmin 게이트 하위에서만 호출된다.
 */
@RestController
@RequestMapping("/admin/announcements")
@RequiresAdmin
class AdminAnnouncementController(
    private val service: AnnouncementService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AnnouncementResponse> = service.listForAdmin(page, size)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: AnnouncementCreateRequest,
    ): AnnouncementResponse = service.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: AnnouncementUpdateRequest,
    ): AnnouncementResponse = service.update(id, request)

    @PostMapping("/{id}/active")
    fun setActive(
        @PathVariable id: Long,
        @RequestBody request: AnnouncementSetActiveRequest,
    ): AnnouncementResponse = service.setActive(id, request.active == true)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        service.delete(id)
    }
}
