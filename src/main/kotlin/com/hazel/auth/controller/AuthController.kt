package com.hazel.auth.controller

import com.hazel.auth.dto.LoginRequest
import com.hazel.auth.dto.LogoutRequest
import com.hazel.auth.dto.RefreshRequest
import com.hazel.auth.dto.SignupRequest
import com.hazel.auth.dto.TokenResponse
import com.hazel.auth.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "회원가입 · 로그인 · 토큰 갱신")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {
    @Operation(summary = "회원가입", description = "사용자 생성 + 기본 설정 시드 후 토큰 발급")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(
        @Valid @RequestBody request: SignupRequest,
    ): TokenResponse = authService.signup(request)

    @Operation(summary = "로그인", description = "이메일/비밀번호 검증 후 access+refresh 발급")
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): TokenResponse = authService.login(request)

    @Operation(summary = "토큰 갱신", description = "refresh 회전 — 기존 refresh 무효화 후 신규 발급")
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): TokenResponse = authService.refresh(request.refreshToken)

    @Operation(summary = "로그아웃", description = "refresh 토큰 무효화")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ) {
        authService.logout(request.refreshToken)
    }
}
