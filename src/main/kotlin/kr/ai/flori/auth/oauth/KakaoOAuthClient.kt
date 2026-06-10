package kr.ai.flori.auth.oauth

/** 카카오 인증코드를 검증해 소셜 신원을 반환. 테스트에서 스텁 가능하도록 인터페이스로 분리. */
interface KakaoOAuthClient {
    fun authenticate(
        code: String,
        redirectUri: String,
    ): KakaoUserInfo
}

/** 카카오 사용자 신원. providerId(카카오 회원번호) 필수, email/nickname은 동의항목에 따라 선택. */
data class KakaoUserInfo(
    val providerId: String,
    val nickname: String?,
    val email: String? = null,
)
