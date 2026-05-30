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
class AdminStatsIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `운영자는 overview 집계를 받는다 — 최소 가입자 1명`() {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)

        mockMvc
            .get("/admin/stats/overview") { header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.users.total") { value(greaterThanOrEqualTo(1)) } }
            .andExpect { jsonPath("$.verifications.pending") { exists() } }
    }

    @Test
    fun `비운영자는 overview 가 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc
            .get("/admin/stats/overview") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }
}
