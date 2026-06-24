package kr.ai.flori.admin

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminBroadcastIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `운영자는 브로드캐스트 초안을 생성한다 — status=draft`() {
        val token = adminToken()
        mockMvc
            .post("/admin/broadcasts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    objectMapper.writeValueAsString(
                        mapOf("title" to "공지 제목", "body" to "공지 내용", "segment" to "all"),
                    )
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { exists() }
                jsonPath("$.status") { value("draft") }
                jsonPath("$.segment") { value("all") }
            }
    }

    @Test
    fun `운영자는 브로드캐스트 목록을 받는다 — 최소 1건`() {
        val token = adminToken()
        createDraft(token)
        mockMvc
            .get("/admin/broadcasts") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) }
            }
    }

    @Test
    fun `세그먼트 미리보기는 targetCount 를 반환한다 — all`() {
        val token = adminToken()
        mockMvc
            .get("/admin/broadcasts/segments/preview?segment=all") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.segment") { value("all") }
                jsonPath("$.targetCount") { value(greaterThanOrEqualTo(0)) }
            }
    }

    @Test
    fun `세그먼트 미리보기는 모든 유효 세그먼트에서 200`() {
        val token = adminToken()
        listOf("verified", "ai_unused", "dormant_14d", "active_7d").forEach { segment ->
            mockMvc
                .get("/admin/broadcasts/segments/preview?segment=$segment") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.segment") { value(segment) }
                    jsonPath("$.targetCount") { value(greaterThanOrEqualTo(0)) }
                }
        }
    }

    @Test
    fun `초안을 발송하면 status=sent 이고 sentAt 이 존재한다`() {
        val token = adminToken()
        val id = createDraft(token)
        mockMvc
            .post("/admin/broadcasts/$id/send") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("sent") }
                jsonPath("$.sentAt") { exists() }
            }
    }

    @Test
    fun `이미 발송된 브로드캐스트 재발송은 409`() {
        val token = adminToken()
        val id = createDraft(token)
        mockMvc
            .post("/admin/broadcasts/$id/send") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
        mockMvc
            .post("/admin/broadcasts/$id/send") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `초안 삭제 후 발송하면 404`() {
        val token = adminToken()
        val id = createDraft(token)
        mockMvc
            .delete("/admin/broadcasts/$id") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { is2xxSuccessful() } }
        mockMvc
            .post("/admin/broadcasts/$id/send") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `잘못된 세그먼트로 생성하면 400`() {
        val token = adminToken()
        mockMvc
            .post("/admin/broadcasts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    objectMapper.writeValueAsString(
                        mapOf("title" to "제목", "body" to "내용", "segment" to "bogus_segment"),
                    )
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `비운영자는 브로드캐스트 목록이 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc
            .get("/admin/broadcasts") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `발송 후 알림 이력에 broadcast 타입 1건 이상이 남는다`() {
        val token = adminToken()
        val id = createDraft(token)
        mockMvc
            .post("/admin/broadcasts/$id/send") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }

        mockMvc
            .get("/admin/notification-logs") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) }
                jsonPath("$[0].type") { value("broadcast") }
            }
    }

    @Test
    fun `알림 이력은 type-status-source 필터에서 200`() {
        val token = adminToken()
        val id = createDraft(token)
        mockMvc
            .post("/admin/broadcasts/$id/send") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }

        listOf("type=broadcast", "status=sent", "source=web").forEach { query ->
            mockMvc
                .get("/admin/notification-logs?$query") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                }.andExpect { status { isOk() } }
        }
    }

    /** "all" 세그먼트 초안을 생성하고 id를 반환한다. */
    private fun createDraft(token: String): Long {
        val response =
            mockMvc
                .post("/admin/broadcasts") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        objectMapper.writeValueAsString(
                            mapOf("title" to "테스트 브로드캐스트", "body" to "테스트 내용", "segment" to "all"),
                        )
                }.andExpect { status { isOk() } }
                .andReturn()
                .response
                .contentAsString
        return objectMapper.readTree(response).get("id").asLong()
    }

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }
}
