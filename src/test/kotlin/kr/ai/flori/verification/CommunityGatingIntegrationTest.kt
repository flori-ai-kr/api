package kr.ai.flori.verification

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class CommunityGatingIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var repository: BusinessVerificationRepository

    @Test
    fun `미인증 사용자는 커뮤니티 목록 조회가 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken

        mockMvc
            .get("/community/posts") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `APPROVED 사용자는 커뮤니티 목록 조회가 200`() {
        val tokenResponse = TestAccounts.register(authService, tokenProvider)
        val userId = tokenProvider.parse(tokenResponse.accessToken)!!.userId
        // 승인은 수동 단계이므로 테스트에선 도메인 메서드 approve()로 시뮬레이션.
        repository.save(
            BusinessVerification(
                userId = userId,
                businessNumber = "1234567890",
                businessName = "플로리",
                representativeName = "홍길동",
                businessLicenseUrl = "https://cdn.example.com/business-licenses/$userId/a.jpg",
            ).apply { approve() },
        )

        mockMvc
            .get("/community/posts") { header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenResponse.accessToken}") }
            .andExpect { status { isOk() } }
    }
}
