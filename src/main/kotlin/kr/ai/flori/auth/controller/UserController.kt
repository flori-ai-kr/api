package kr.ai.flori.auth.controller

import jakarta.validation.Valid
import kr.ai.flori.auth.dto.UpdateEmailRequest
import kr.ai.flori.auth.dto.UserResponse
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.dto.OnboardingRequest
import kr.ai.flori.user.service.OnboardingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 현재 로그인 사용자 조회 · 이메일 보완 · 온보딩 제출. 보호 엔드포인트(인증 필요) —
 * JWT 필터 + TenantContext 격리를 활용한다. user_id는 항상 TenantContext에서만 도출한다.
 */
@RestController
class UserController(
    private val authService: AuthService,
    private val onboardingService: OnboardingService,
) {
    @GetMapping("/me")
    fun me(): UserResponse = authService.me(TenantContext.currentUserId())

    @PatchMapping("/me/email")
    fun updateEmail(
        @Valid @RequestBody request: UpdateEmailRequest,
    ): UserResponse = authService.updateEmail(TenantContext.currentUserId(), request.email)

    @PostMapping("/me/onboarding")
    fun submitOnboarding(
        @Valid @RequestBody request: OnboardingRequest,
    ): UserResponse = onboardingService.submit(TenantContext.currentUserId(), request)
}
