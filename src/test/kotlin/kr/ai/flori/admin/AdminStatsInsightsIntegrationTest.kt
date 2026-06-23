package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.hamcrest.Matchers.greaterThanOrEqualTo
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
class AdminStatsInsightsIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `운영자는 활성화 퍼널을 받는다 — 첫 단계 key 는 signup`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/funnel") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$") { isArray() } }
            .andExpect { jsonPath("$[0].key") { value("signup") } }
            .andExpect { jsonPath("$[0].label") { exists() } }
            .andExpect { jsonPath("$[0].count") { exists() } }
    }

    @Test
    fun `운영자는 탈퇴 사유 집계를 받는다 — 배열`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/churn-reasons?days=30") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$") { isArray() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(0)) } }
    }

    @Test
    fun `운영자는 리텐션 코호트를 받는다 — 배열`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/retention") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$") { isArray() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(0)) } }
    }

    @Test
    fun `비운영자는 funnel 이 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc
            .get("/admin/stats/funnel") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }
}
