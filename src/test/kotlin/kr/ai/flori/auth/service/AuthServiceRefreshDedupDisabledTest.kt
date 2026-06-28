package kr.ai.flori.auth.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.RegisterCompleteRequest
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.UUID

/**
 * 멱등 윈도 비활성(jwt.refresh-dedup-ttl-seconds=0) 시 동작 검증.
 * = "윈도 밖" 의미와 동일: 회전된 refresh 토큰의 재사용은 INVALID_TOKEN으로 거부된다(회전 reuse 탐지 유지).
 *
 * 멱등 윈도 내 동작은 [AuthServiceIntegrationTest]의 기본(30초) 컨텍스트에서 검증한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@Import(AuthServiceIntegrationTest.StubSocialConfig::class)
@TestPropertySource(
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "jwt.refresh-dedup-ttl-seconds=0",
    ],
)
class AuthServiceRefreshDedupDisabledTest {
    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Test
    fun `멱등 비활성 시 회전된 refresh 토큰 재사용은 INVALID_TOKEN`() {
        val email = "user-${UUID.randomUUID()}@flori.dev"
        val registerToken = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", email, "닉")
        val first =
            authService.registerComplete(
                RegisterCompleteRequest(
                    registerToken = registerToken,
                    ownerName = "홍길동",
                    storeName = "헤이즐 플라워",
                    phoneNumber = "01012345678",
                    nickname = "헤이즐-${UUID.randomUUID()}",
                    email = email,
                    regionSido = "서울특별시",
                    ownerAgeRange = "30대",
                    referralSources = listOf("인스타그램"),
                    termsAgreed = true,
                    privacyAgreed = true,
                ),
            )

        val rotated = authService.refresh(first.refreshToken)
        assertThat(rotated.accessToken).isNotBlank()

        assertThatThrownBy { authService.refresh(first.refreshToken) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.INVALID_TOKEN)
            }
    }
}
