package kr.ai.flori.announcement.controller

import kr.ai.flori.announcement.dto.AnnouncementResponse
import kr.ai.flori.announcement.service.AnnouncementService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 점주용 공지 배너. JWT 인증만 요구하고 사업자 인증 게이트는 두지 않는다.
 * 공지는 인증 전(미인증/대기 화면)에도 노출되어야 하므로 @RequiresBusinessVerified를 붙이지 않는다.
 */
@RestController
@RequestMapping("/announcements")
class AnnouncementController(
    private val service: AnnouncementService,
) {
    @GetMapping
    fun listActive(
        @RequestParam(required = false) placement: String?,
    ): List<AnnouncementResponse> = service.listActive(placement)

    @PostMapping("/{id}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun click(
        @PathVariable id: Long,
    ) {
        service.click(id)
    }
}
