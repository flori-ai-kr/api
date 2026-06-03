package kr.ai.flori.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(description = "닉네임 사용 가능 응답. 사용 가능하면 available=true(200), 이미 사용 중이면 409(E-AUTH-003).")
data class NicknameAvailabilityResponse(
    @field:Schema(description = "사용 가능 여부")
    val available: Boolean,
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

@Schema(
    description =
        "카카오 OAuth 로그인 요청. 두 경로를 모두 지원한다 — " +
            "웹(code+redirectUri 교환) / 앱(네이티브 SDK accessToken). accessToken이 있으면 code 교환을 건너뛴다.",
)
data class KakaoOAuthRequest(
    @field:Schema(description = "카카오 네이티브 SDK가 발급한 access token(앱). 있으면 code 교환 생략.")
    val accessToken: String? = null,
    @field:Schema(description = "카카오 authorize에서 받은 authorization code(웹). accessToken 미지정 시 필수.")
    val code: String? = null,
    @field:Schema(description = "앱에서 사용한 redirect URI(웹 code 교환 시 일치 필요).")
    val redirectUri: String? = null,
) {
    @get:AssertTrue(message = "accessToken 또는 (code, redirectUri)가 필요합니다")
    @get:Schema(hidden = true)
    val isValidRequest: Boolean
        get() =
            !accessToken.isNullOrBlank() ||
                (!code.isNullOrBlank() && !redirectUri.isNullOrBlank())
}

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

@Schema(description = "이메일 변경 요청(로그인 사용자). 형식 검증 + 중복 검사 후 저장한다.")
data class UpdateEmailRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Schema(description = "설정할 이메일", example = "florist@flori.kr")
    val email: String,
)

/**
 * 소셜 OAuth 단일 결과 응답.
 * - registered=true: 기존 사용자 → 로그인 완료. [token] 채워짐, register* 필드는 null.
 * - registered=false: 신규 신원 → User 미생성. [registerToken] + 소셜 기본값(이메일/닉네임)을 주어
 *   웹이 온보딩 화면을 프리필하고, 완료 시 register/complete로 가입을 마무리한다.
 */
@Schema(description = "소셜 로그인 결과. registered가 false면 온보딩(register/complete)이 필요하다.")
data class OAuthResult(
    @field:Schema(description = "기존 사용자 여부. true면 token, false면 registerToken+소셜 기본값이 채워진다.")
    val registered: Boolean,
    @field:Schema(description = "로그인 완료 토큰(registered=true일 때만)")
    val token: TokenResponse? = null,
    @field:Schema(description = "가입 대기 토큰(registered=false일 때만, 5분 TTL). register/complete에 그대로 전달한다.")
    val registerToken: String? = null,
    @field:Schema(description = "소셜이 제공한 이메일(온보딩 이메일 입력 기본값, 없으면 null)")
    val socialEmail: String? = null,
    @field:Schema(description = "소셜이 제공한 닉네임(온보딩 닉네임 입력 기본값, 없으면 null)")
    val socialNickname: String? = null,
)

/**
 * 가입 완료(= 온보딩) 요청. registerToken을 자격증명으로 사용하며(아직 User 없음, 인증 불필요),
 * 온보딩 입력으로 User + 가게 프로필을 한 번에 생성한다.
 *
 * 매핑: nickname → users.nickname(계정 표시명), storeName → user_profiles.store_name(가게명),
 *      email → users.email(수정 가능). regionSido 필수, 그 외 지역/나이대/관심사/주력은 선택.
 */
@Schema(description = "가입 완료(온보딩) 요청. registerToken으로 신원을 검증하고 User+프로필을 생성한다.")
data class RegisterCompleteRequest(
    @field:NotBlank(message = "registerToken은 필수입니다")
    @field:Schema(description = "소셜 로그인이 반환한 registerToken(5분 TTL)")
    val registerToken: String,
    @field:NotBlank(message = "가게명은 필수입니다")
    @field:Schema(description = "가게명", example = "헤이즐 플라워")
    val storeName: String,
    @field:NotBlank(message = "닉네임은 필수입니다")
    @field:Schema(description = "계정 표시명(기본값=소셜 닉네임, 수정 가능)", example = "헤이즐")
    val nickname: String,
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Schema(description = "로그인 이메일(기본값=소셜 이메일, 수정 가능)", example = "florist@flori.kr")
    val email: String,
    @field:NotBlank(message = "시/도는 필수입니다")
    @field:Schema(description = "시/도(웹 enum 값 문자열)", example = "서울특별시")
    val regionSido: String,
    @field:Schema(description = "시군구(선택)", example = "강남구")
    val regionSigungu: String? = null,
    @field:Schema(description = "사장님 나이대(선택, 단일)", example = "30대")
    val ownerAgeRange: String? = null,
    @field:Schema(description = "관심사(선택, 복수)", example = "[\"웨딩\",\"개업화환\"]")
    val interests: List<String>? = null,
    @field:Schema(description = "가게 주력(선택, 복수)", example = "[\"꽃다발\",\"화분\"]")
    val specialties: List<String>? = null,
)
