package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.ai.client.AiBlogDraft
import kr.ai.flori.ai.client.AiBlogResult
import kr.ai.flori.ai.client.AiBlogSection
import kr.ai.flori.ai.client.AiServerClient
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * AdminPromptController 통합테스트(SPEC-AI-008). CRUD·활성화·삭제거부·@RequiresAdmin(403)·플레이그라운드를
 * MockMvc로 검증한다. ai-server 호출([AiServerClient])만 mock해 preview가 저장 없이 초안을 반환하는지 본다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminPromptIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @MockitoBean private lateinit var aiClient: AiServerClient

    @Test
    fun `운영자는 프롬프트를 생성하면 201 과 id 를 받는다`() {
        val token = adminToken()
        mockMvc
            .post("/admin/prompts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":"${uniqueVersion()}","systemMd":"테스트 시스템","model":"claude-sonnet-4-6"}"""
            }.andExpect { status { isCreated() } }
            .andExpect { jsonPath("$.id") { exists() } }
            .andExpect { jsonPath("$.isActive") { value(false) } }
    }

    @Test
    fun `운영자 목록은 최소 1건 이상이다`() {
        val token = adminToken()
        createPrompt(token, activate = true)
        mockMvc
            .get("/admin/prompts?channel=blog") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)) } }
    }

    @Test
    fun `운영자는 프롬프트를 활성화하면 기존 active 가 비활성된다`() {
        val token = adminToken()
        val first = createPrompt(token, activate = true)
        val second = createPrompt(token)

        mockMvc
            .post("/admin/prompts/$second/activate") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.isActive") { value(true) } }

        mockMvc
            .get("/admin/prompts/$first") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.isActive") { value(false) } }
    }

    @Test
    fun `active 프롬프트 삭제는 422`() {
        val token = adminToken()
        val id = createPrompt(token, activate = true)
        mockMvc
            .delete("/admin/prompts/$id") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `비활성 프롬프트는 삭제된다`() {
        val token = adminToken()
        val id = createPrompt(token)
        mockMvc
            .delete("/admin/prompts/$id") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `화이트리스트 밖 모델 생성은 422`() {
        val token = adminToken()
        mockMvc
            .post("/admin/prompts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":"${uniqueVersion()}","systemMd":"x","model":"gpt-4o"}"""
            }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `비운영자가 접근하면 403`() {
        val token = userToken()
        mockMvc
            .get("/admin/prompts?channel=blog") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `플레이그라운드 preview 는 저장 없이 초안을 반환한다`() {
        val token = adminToken()
        Mockito
            .`when`(aiClient.generateBlog(anyString(), anyLong(), anyBlogCall()))
            .thenReturn(
                AiBlogResult(
                    draft = AiBlogDraft(title = "미리보기 제목", sections = listOf(AiBlogSection("h", "b"))),
                    model = "claude-sonnet-4-6",
                ),
            )

        mockMvc
            .post("/admin/prompts/preview") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"promptDraft":{"systemMd":"커스텀 시스템","model":"claude-sonnet-4-6","temperature":0.7},
                       "sampleInput":{"keyword":"장미 꽃다발"}}"""
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.draft.title") { value("미리보기 제목") } }
            .andExpect { jsonPath("$.model") { value("claude-sonnet-4-6") } }
    }

    // ─────────────────────────── helpers ───────────────────────────

    private fun uniqueVersion() = "v-${UUID.randomUUID()}"

    private fun createPrompt(
        token: String,
        activate: Boolean = false,
    ): Long {
        val response =
            mockMvc
                .post("/admin/prompts") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"version":"${uniqueVersion()}","systemMd":"테스트 시스템","activate":$activate}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        return ID_REGEX.find(response)!!.groupValues[1].toLong()
    }

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }

    private fun userToken(): String = TestAccounts.register(authService, tokenProvider).accessToken

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun anyBlogCall(): kr.ai.flori.ai.client.AiBlogCall {
        Mockito.any(kr.ai.flori.ai.client.AiBlogCall::class.java)
        return uninitialized()
    }

    private companion object {
        val ID_REGEX = Regex("\"id\"\\s*:\\s*(\\d+)")
    }
}
