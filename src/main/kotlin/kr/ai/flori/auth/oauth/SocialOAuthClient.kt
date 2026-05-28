package kr.ai.flori.auth.oauth

/**
 * 소셜 로그인 제공자 추상화. 인증코드를 검증해 공통 신원([SocialUserInfo])을 반환한다.
 *
 * 제공자별 구현체는 Spring 빈 이름(KAKAO/GOOGLE/NAVER)을 키로 [Map] 주입돼 선택된다.
 * 테스트에서는 익명 구현으로 스텁 가능하도록 인터페이스로 분리한다.
 *
 * @MX:ANCHOR: [AUTO] 모든 소셜 제공자 구현(Kakao/Google/Naver)이 의존하는 인증 경계 계약.
 * @MX:REASON: 구현체 3개 + AuthService가 의존(fan_in>=3). 시그니처 변경은 모든 제공자에 파급된다.
 */
interface SocialOAuthClient {
    fun authenticate(
        code: String,
        redirectUri: String,
        state: String?,
    ): SocialUserInfo
}

/**
 * 제공자 중립 소셜 신원.
 * - [provider] 대문자 제공자 식별자(KAKAO/GOOGLE/NAVER). User.provider와 일치.
 * - [providerId] 제공자 내 고유 식별자(문자열화).
 * - [email] 제공자가 제공한 이메일(없거나 미수집이면 null).
 * - [nickname] 표시 이름(없으면 null).
 */
data class SocialUserInfo(
    val provider: String,
    val providerId: String,
    val email: String?,
    val nickname: String?,
)
