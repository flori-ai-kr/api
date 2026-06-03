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

    @Test
    fun `range=30d 이면 comparison 객체가 존재한다`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/overview?range=30d") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.comparison") { exists() } }
    }

    @Test
    fun `signups 시계열은 7d 면 7개 점을 반환한다`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/timeseries?metric=signups&range=7d") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(7) } }
            .andExpect { jsonPath("$[0].date") { exists() } }
            .andExpect { jsonPath("$[0].count") { exists() } }
    }

    @Test
    fun `sales 시계열도 동작한다`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/timeseries?metric=sales&range=30d") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(30) } }
    }

    @Test
    fun `알 수 없는 metric 은 400`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/timeseries?metric=bogus&range=7d") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `알 수 없는 range 는 400 (silent fallback 금지)`() {
        val token = adminToken()
        mockMvc
            .get("/admin/stats/timeseries?metric=signups&range=bogus") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isBadRequest() } }
    }

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }
}
