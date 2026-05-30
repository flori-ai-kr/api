package kr.ai.flori.admin

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.hamcrest.Matchers.greaterThanOrEqualTo
import kr.ai.flori.admin.dto.SetActiveRequest
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `운영자는 유저 목록을 조회하고 is_active 를 토글한다`() {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val uid = tokenProvider.parse(tokens.accessToken)!!.userId
        val user = userRepository.findById(uid).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)

        mockMvc
            .get("/admin/users") { header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.total") { value(greaterThanOrEqualTo(1)) } }

        mockMvc
            .post("/admin/users/$uid/active") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(SetActiveRequest(active = false))
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.isActive") { value(false) } }
    }

    @Test
    fun `비운영자는 유저 목록이 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc
            .get("/admin/users") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `운영자는 구독 목록을 조회한다`() {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)

        mockMvc
            .get("/admin/subscriptions") { header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}") }
            .andExpect { status { isOk() } }
    }
}
