package kr.ai.flori.auth.oauth

/** 카카오 인증코드를 검증해 소셜 신원을 반환. 테스트에서 스텁 가능하도록 인터페이스로 분리. */
interface KakaoOAuthClient {
    fun authenticate(
        code: String,
        redirectUri: String,
    ): KakaoUserInfo
}

/** 카카오 사용자 신원. providerId(카카오 회원번호)만 필수, 이메일은 MVP 미수집. */
data class KakaoUserInfo(
    val providerId: String,
    val nickname: String?,
)
