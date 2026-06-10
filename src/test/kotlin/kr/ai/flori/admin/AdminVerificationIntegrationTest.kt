package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminVerificationIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var verificationRepository: BusinessVerificationRepository

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }

    private fun pendingFor(): Long {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val uid = tokenProvider.parse(tokens.accessToken)!!.userId
        return verificationRepository
            .save(
                BusinessVerification(
                    userId = uid,
                    businessNumber = "1234567890",
                    businessName = "플로리",
                    representativeName = "홍길동",
                    businessLicenseUrl = "https://cdn.example.com/business-licenses/$uid/a.jpg",
                ),
            ).id!!
    }

    @Test
    fun `운영자는 PENDING 목록을 조회한다`() {
        val token = adminToken()
        pendingFor()
        mockMvc
            .get("/admin/verifications?status=PENDING") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$[0].status") { value("PENDING") } }
    }

    @Test
    fun `승인하면 APPROVED 로 전이하고 재승인은 409`() {
        val token = adminToken()
        val id = pendingFor()
        mockMvc
            .post("/admin/verifications/$id/approve") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.status") { value("APPROVED") } }
        mockMvc
            .post("/admin/verifications/$id/approve") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `비운영자는 목록 조회가 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc
            .get("/admin/verifications") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }
}
