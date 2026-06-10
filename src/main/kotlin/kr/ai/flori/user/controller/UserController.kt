package kr.ai.flori.user.controller

import jakarta.validation.Valid
import kr.ai.flori.auth.dto.UpdateEmailRequest
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.dto.DeleteAccountRequest
import kr.ai.flori.user.dto.FullProfileResponse
import kr.ai.flori.user.dto.OnboardingRequest
import kr.ai.flori.user.dto.ProfileUploadTargetRequest
import kr.ai.flori.user.dto.ProfileUploadTargetResponse
import kr.ai.flori.user.dto.UserResponse
import kr.ai.flori.user.service.OnboardingService
import kr.ai.flori.user.service.ProfileService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val authService: AuthService,
    private val onboardingService: OnboardingService,
    private val profileService: ProfileService,
) {
    @GetMapping("/me")
    fun me(): UserResponse = authService.me(TenantContext.currentUserId())

    @PatchMapping("/me/email")
    fun updateEmail(
        @Valid @RequestBody request: UpdateEmailRequest,
    ): UserResponse = authService.updateEmail(TenantContext.currentUserId(), request.email)

    @GetMapping("/me/profile")
    fun getProfile(): FullProfileResponse = profileService.getFullProfile(TenantContext.currentUserId())

    @PatchMapping("/me/profile")
    fun updateProfile(
        @Valid @RequestBody request: OnboardingRequest,
    ): UserResponse = onboardingService.submit(TenantContext.currentUserId(), request)

    @PostMapping("/me/profile")
    fun upsertProfile(
        @Valid @RequestBody request: OnboardingRequest,
    ): UserResponse = onboardingService.submit(TenantContext.currentUserId(), request)

    @PostMapping("/me/profile/upload-target")
    fun profileUploadTarget(
        @Valid @RequestBody request: ProfileUploadTargetRequest,
    ): ProfileUploadTargetResponse = profileService.createUploadTarget(TenantContext.currentUserId(), request.contentType)

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(
        @RequestBody(required = false) request: DeleteAccountRequest?,
    ) {
        profileService.deleteAccount(
            TenantContext.currentUserId(),
            request?.reason,
            request?.detail,
        )
    }
}
