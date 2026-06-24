package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasItem
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
class AdminAuditLogIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var verificationRepository: BusinessVerificationRepository

    @Test
    fun `인증 승인은 감사 로그를 남긴다 — action VERIFICATION_APPROVE 로 필터되어 보인다`() {
        val ownerId = TestAccounts.register(authService, tokenProvider).let { tokenProvider.parse(it.accessToken)!!.userId }
        val verificationId = seedPendingVerification(ownerId)
        val adminTok = adminToken()

        mockMvc
            .post("/admin/verifications/$verificationId/approve") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok")
            }.andExpect { status { isOk() } }

        mockMvc
            .get("/admin/audit-logs") { header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
            .andExpect { jsonPath("$[0].action") { exists() } }
            .andExpect { jsonPath("$[0].actorEmail") { exists() } }
            .andExpect { jsonPath("$[0].createdAt") { exists() } }

        mockMvc
            .get("/admin/audit-logs?action=VERIFICATION_APPROVE") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok")
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
            .andExpect { jsonPath("$[*].action") { value(hasItem("VERIFICATION_APPROVE")) } }
    }

    @Test
    fun `유저 비활성화는 USER_DEACTIVATE 감사 로그를 남긴다`() {
        val targetUserId = TestAccounts.register(authService, tokenProvider).let { tokenProvider.parse(it.accessToken)!!.userId }
        val adminTok = adminToken()

        mockMvc
            .post("/admin/users/$targetUserId/active") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok")
                contentType = MediaType.APPLICATION_JSON
                content = """{"active":false}"""
            }.andExpect { status { isOk() } }

        mockMvc
            .get("/admin/audit-logs?action=USER_DEACTIVATE") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok")
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
            .andExpect { jsonPath("$[*].action") { value(hasItem("USER_DEACTIVATE")) } }
    }

    @Test
    fun `비운영자는 감사 로그 조회가 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc
            .get("/admin/audit-logs") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    /** 지정 점주에 대해 PENDING 사업자 인증 1건을 시드하고 id를 반환한다. */
    private fun seedPendingVerification(ownerId: Long): Long =
        verificationRepository
            .save(
                BusinessVerification(
                    userId = ownerId,
                    businessNumber = "1234567890",
                    businessName = "테스트 꽃집",
                    representativeName = "홍길동",
                    businessLicenseUrl = "https://cdn.flori.test/license.png",
                ),
            ).id!!

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }
}
