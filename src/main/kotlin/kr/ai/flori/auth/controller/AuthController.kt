package kr.ai.flori.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.ai.flori.auth.dto.GoogleOAuthRequest
import kr.ai.flori.auth.dto.KakaoOAuthRequest
import kr.ai.flori.auth.dto.LogoutRequest
import kr.ai.flori.auth.dto.NaverOAuthRequest
import kr.ai.flori.auth.dto.NicknameAvailabilityResponse
import kr.ai.flori.auth.dto.OAuthResult
import kr.ai.flori.auth.dto.RefreshRequest
import kr.ai.flori.auth.dto.RegisterCompleteRequest
import kr.ai.flori.auth.dto.TokenResponse
import kr.ai.flori.auth.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "소셜 로그인 · 가입 완료 · 토큰 갱신")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {
    @Operation(
        summary = "카카오 로그인",
        description =
            "카카오 신원 검증. 웹은 code+redirectUri(교환), 앱은 네이티브 SDK accessToken으로 호출한다. " +
                "기존 사용자면 토큰, 신규면 registerToken+소셜 기본값 반환(가입 필요).",
    )
    @PostMapping("/oauth/kakao")
    fun kakaoLogin(
        @Valid @RequestBody request: KakaoOAuthRequest,
    ): OAuthResult =
        if (!request.accessToken.isNullOrBlank()) {
            authService.oauthLoginWithAccessToken("KAKAO", request.accessToken)
        } else {
            authService.oauthLogin("KAKAO", request.code.orEmpty(), request.redirectUri.orEmpty(), null)
        }

    @Operation(
        summary = "구글 로그인",
        description = "구글 인증코드 검증. 기존 사용자면 토큰, 신규면 registerToken+소셜 기본값 반환(가입 필요).",
    )
    @PostMapping("/oauth/google")
    fun googleLogin(
        @Valid @RequestBody request: GoogleOAuthRequest,
    ): OAuthResult = authService.oauthLogin("GOOGLE", request.code, request.redirectUri, null)

    @Operation(
        summary = "네이버 로그인",
        description = "네이버 인증코드 검증. 기존 사용자면 토큰, 신규면 registerToken+소셜 기본값 반환(가입 필요). state 필수.",
    )
    @PostMapping("/oauth/naver")
    fun naverLogin(
        @Valid @RequestBody request: NaverOAuthRequest,
    ): OAuthResult = authService.oauthLogin("NAVER", request.code, request.redirectUri, request.state)

    @Operation(
        summary = "가입 완료(온보딩)",
        description = "registerToken + 온보딩 입력으로 User+가게 프로필 생성 후 토큰 발급. 신원은 registerToken에서만 도출.",
    )
    @PostMapping("/register/complete")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerComplete(
        @Valid @RequestBody request: RegisterCompleteRequest,
    ): TokenResponse = authService.registerComplete(request)

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

    @Operation(
        summary = "닉네임 중복 확인",
        description = "가입 화면 중복확인 버튼용. 사용 가능하면 200(available=true), 이미 사용 중이면 409(E-AUTH-003).",
    )
    @GetMapping("/nickname/check")
    fun checkNickname(
        @RequestParam nickname: String,
    ): NicknameAvailabilityResponse {
        authService.ensureNicknameAvailable(nickname)
        return NicknameAvailabilityResponse(available = true)
    }
}
