package kr.ai.flori.support

import kr.ai.flori.auth.dto.RegisterCompleteRequest
import kr.ai.flori.auth.dto.TokenResponse
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import java.util.UUID

/**
 * 테스트용 계정 부트스트랩 헬퍼.
 *
 * 소셜 전용 전환으로 signup이 사라졌으므로, 테스트가 사용자를 만들 때는 실제 신규 경로
 * (registerToken 발급 → register/complete)를 그대로 태운다. User + 가게 프로필 + 기본 설정 시드가
 * 운영과 동일하게 생성되므로, 기존 service 테스트의 부트스트랩을 한 줄로 대체할 수 있다.
 */
object TestAccounts {
    /**
     * 신규 소셜 신원으로 가입을 완료하고 토큰을 반환한다. provider/providerId는 매 호출마다 고유.
     * email을 생략하면 고유 이메일을 자동 생성한다.
     */
    fun register(
        authService: AuthService,
        tokenProvider: JwtTokenProvider,
        email: String = "user-${UUID.randomUUID()}@flori.dev",
        provider: String = "GOOGLE",
        providerId: String = "$provider-${UUID.randomUUID()}",
        // 닉네임(users.nickname)은 전역 유일(uq_users_nickname)이므로 호출마다 고유 기본값을 생성해 충돌을 피한다.
        nickname: String = "사장님-${UUID.randomUUID()}",
        storeName: String = "테스트 가게",
        phoneNumber: String = "01012345678",
        regionSido: String = "서울특별시",
        ownerName: String = "홍길동",
        ownerAgeRange: String = "30대",
        referralSources: List<String> = listOf("인스타그램"),
    ): TokenResponse {
        val registerToken = tokenProvider.generateRegisterToken(provider, providerId, email, nickname)
        return authService.registerComplete(
            RegisterCompleteRequest(
                registerToken = registerToken,
                ownerName = ownerName,
                storeName = storeName,
                phoneNumber = phoneNumber,
                nickname = nickname,
                email = email,
                regionSido = regionSido,
                ownerAgeRange = ownerAgeRange,
                referralSources = referralSources,
                termsAgreed = true,
                privacyAgreed = true,
            ),
        )
    }
}
