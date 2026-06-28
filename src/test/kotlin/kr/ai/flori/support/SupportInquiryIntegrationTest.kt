package kr.ai.flori.support

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.user.repository.UserRepository
import org.hamcrest.Matchers.greaterThanOrEqualTo
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
class SupportInquiryIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `점주는 문의를 생성하면 open 상태로 id 를 받는다`() {
        val token = userToken()
        mockMvc
            .post("/inquiries") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"category":"bug","title":"버그 신고","body":"앱이 멈춰요"}"""
            }.andExpect { status { is2xxSuccessful() } }
            .andExpect { jsonPath("$.id") { exists() } }
            .andExpect { jsonPath("$.status") { value("open") } }
    }

    @Test
    fun `점주는 자신의 문의 목록을 조회한다 — 최소 1건`() {
        val token = userToken()
        mockMvc
            .post("/inquiries") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"category":"feedback","title":"피드백","body":"좋아요"}"""
            }.andExpect { status { is2xxSuccessful() } }

        mockMvc
            .get("/inquiries") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
    }

    @Test
    fun `알 수 없는 카테고리는 400`() {
        val token = userToken()
        mockMvc
            .post("/inquiries") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"category":"nonsense","title":"제목","body":"내용"}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `운영자는 open 상태 문의 인박스를 조회한다 — 최소 1건`() {
        val userTok = userToken()
        createInquiry(userTok)
        val adminTok = adminToken()

        mockMvc
            .get("/admin/inquiries?status=open") { header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
    }

    @Test
    fun `운영자는 문의 상세를 조회한다`() {
        val id = createInquiry(userToken())
        val adminTok = adminToken()

        mockMvc
            .get("/admin/inquiries/$id") { header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.id") { value(id.toInt()) } }
            .andExpect { jsonPath("$.title") { exists() } }
    }

    @Test
    fun `운영자는 문의에 답변하면 resolved 로 바뀌고 answer 가 설정된다`() {
        val id = createInquiry(userToken())
        val adminTok = adminToken()

        mockMvc
            .post("/admin/inquiries/$id/answer") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok")
                contentType = MediaType.APPLICATION_JSON
                content = """{"answer":"확인했습니다","status":"resolved"}"""
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.status") { value("resolved") } }
            .andExpect { jsonPath("$.answer") { value("확인했습니다") } }
    }

    @Test
    fun `운영자는 문의 상태를 closed 로 변경한다`() {
        val id = createInquiry(userToken())
        val adminTok = adminToken()

        mockMvc
            .post("/admin/inquiries/$id/status") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok")
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"closed"}"""
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.status") { value("closed") } }
    }

    @Test
    fun `운영자가 알 수 없는 상태로 변경하면 400`() {
        val id = createInquiry(userToken())
        val adminTok = adminToken()

        mockMvc
            .post("/admin/inquiries/$id/status") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminTok")
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"nonsense"}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `비운영자는 admin 인박스 조회가 403`() {
        val token = userToken()
        mockMvc
            .get("/admin/inquiries") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    /** 점주 토큰으로 문의 1건을 생성하고 id를 반환한다. */
    private fun createInquiry(token: String): Long {
        val body =
            mockMvc
                .post("/inquiries") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"category":"bug","title":"버그 신고","body":"앱이 멈춰요"}"""
                }.andExpect { status { is2xxSuccessful() } }
                .andReturn()
                .response
                .contentAsString
        return objectMapper.readTree(body)["id"].asLong()
    }

    private fun userToken(): String = TestAccounts.register(authService, tokenProvider).accessToken

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }
}
