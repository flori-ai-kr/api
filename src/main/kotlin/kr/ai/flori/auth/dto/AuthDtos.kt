package kr.ai.flori.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "회원가입 요청. 가입 시 사용자별 기본 카테고리/결제방식/카드사를 시드한다.")
data class SignupRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Schema(description = "로그인 이메일", example = "florist@flori.kr")
    val email: String,
    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
    @field:Schema(description = "비밀번호(8~72자). BCrypt로 저장.", example = "flori1234", minLength = 8, maxLength = 72)
    val password: String,
    @field:Schema(description = "표시 이름(선택)", example = "헤이즐 플라워")
    val name: String? = null,
)

@Schema(description = "로그인 요청")
data class LoginRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Schema(description = "로그인 이메일", example = "florist@flori.kr")
    val email: String,
    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Schema(description = "비밀번호", example = "flori1234")
    val password: String,
)

@Schema(description = "access 토큰 재발급 요청(refresh 로테이션)")
data class RefreshRequest(
    @field:NotBlank(message = "refresh 토큰은 필수입니다")
    @field:Schema(description = "발급받은 refresh 토큰")
    val refreshToken: String,
)

@Schema(description = "로그아웃 요청. 해당 refresh 토큰을 무효화한다.")
data class LogoutRequest(
    @field:NotBlank(message = "refresh 토큰은 필수입니다")
    @field:Schema(description = "무효화할 refresh 토큰")
    val refreshToken: String,
)

@Schema(description = "토큰 발급 응답")
data class TokenResponse(
    @field:Schema(description = "API 호출에 쓰는 access 토큰(짧은 TTL)")
    val accessToken: String,
    @field:Schema(description = "access 재발급용 refresh 토큰(로테이션)")
    val refreshToken: String,
    @field:Schema(description = "access 토큰 만료까지 남은 초", example = "3600")
    val expiresIn: Long,
    @field:Schema(description = "토큰 타입", example = "Bearer")
    val tokenType: String = "Bearer",
)
