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

@Schema(description = "카카오 OAuth 로그인 요청(인증코드 교환).")
data class KakaoOAuthRequest(
    @field:NotBlank(message = "인증코드는 필수입니다")
    @field:Schema(description = "카카오 authorize에서 받은 authorization code")
    val code: String,
    @field:NotBlank(message = "redirectUri는 필수입니다")
    @field:Schema(description = "앱에서 사용한 redirect URI(코드 교환 시 일치 필요)")
    val redirectUri: String,
)

@Schema(description = "구글 OAuth 로그인 요청(인증코드 교환).")
data class GoogleOAuthRequest(
    @field:NotBlank(message = "인증코드는 필수입니다")
    @field:Schema(description = "구글 authorize에서 받은 authorization code")
    val code: String,
    @field:NotBlank(message = "redirectUri는 필수입니다")
    @field:Schema(description = "앱에서 사용한 redirect URI(코드 교환 시 일치 필요)")
    val redirectUri: String,
)

@Schema(description = "네이버 OAuth 로그인 요청(인증코드 교환). state 필수.")
data class NaverOAuthRequest(
    @field:NotBlank(message = "인증코드는 필수입니다")
    @field:Schema(description = "네이버 authorize에서 받은 authorization code")
    val code: String,
    @field:NotBlank(message = "redirectUri는 필수입니다")
    @field:Schema(description = "앱에서 사용한 redirect URI(코드 교환 시 일치 필요)")
    val redirectUri: String,
    @field:NotBlank(message = "state는 필수입니다")
    @field:Schema(description = "CSRF 방지용 state(authorize 시 전달한 값과 동일)")
    val state: String,
)

@Schema(description = "소셜 가입 후 이메일 보완 요청. 형식 검증 + 중복 검사 후 저장한다.")
data class UpdateEmailRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Schema(description = "설정할 이메일", example = "florist@flori.kr")
    val email: String,
)
