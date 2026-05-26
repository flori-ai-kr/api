package kr.ai.flori.settings.controller

import kr.ai.flori.settings.dto.UserPreferencesResponse
import kr.ai.flori.settings.service.UserPreferenceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 유저 설정(하단바 커스터마이즈) 엔드포인트.
 * 서비스는 SPEC-012에서 만들어졌으나 라우트가 누락되어 웹이 500을 받던 것을 연결한다.
 * 멀티테넌시는 [UserPreferenceService]가 TenantContext로 처리한다.
 */
@RestController
@RequestMapping("/settings")
class UserPreferenceController(
    private val service: UserPreferenceService,
) {
    @GetMapping("/preferences")
    fun get(): UserPreferencesResponse = service.get()

    @PutMapping("/preferences/bottom-nav")
    fun updateBottomNav(
        @RequestBody request: BottomNavUpdateRequest,
    ): UserPreferencesResponse = service.updateBottomNav(request.items)
}

data class BottomNavUpdateRequest(
    val items: List<String>,
)
