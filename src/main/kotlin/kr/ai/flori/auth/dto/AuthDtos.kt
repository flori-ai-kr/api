package kr.ai.flori.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:NotBlank(message = "이메일은 필수입니다")
    val email: String,
    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
    val password: String,
    val name: String? = null,
)

data class LoginRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:NotBlank(message = "이메일은 필수입니다")
    val email: String,
    @field:NotBlank(message = "비밀번호는 필수입니다")
    val password: String,
)

data class RefreshRequest(
    @field:NotBlank(message = "refresh 토큰은 필수입니다")
    val refreshToken: String,
)

data class LogoutRequest(
    @field:NotBlank(message = "refresh 토큰은 필수입니다")
    val refreshToken: String,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)
