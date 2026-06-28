package kr.ai.flori.announcement

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.announcement.repository.AnnouncementRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AnnouncementIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var announcementRepository: AnnouncementRepository

    // ─────────────────────────── ADMIN CRUD ───────────────────────────

    @Test
    fun `운영자는 공지를 생성하면 201 과 id 를 받는다`() {
        val token = adminToken()
        mockMvc
            .post("/admin/announcements") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"placement":"modal","title":"점검 안내","body":"오늘 밤 점검합니다","isActive":true}"""
            }.andExpect { status { isCreated() } }
            .andExpect { jsonPath("$.id") { exists() } }
            .andExpect { jsonPath("$.placement") { value("modal") } }
            .andExpect { jsonPath("$.title") { value("점검 안내") } }
            .andExpect { jsonPath("$.isActive") { value(true) } }
    }

    @Test
    fun `운영자 목록은 최소 1건 이상이다`() {
        val token = adminToken()
        createAnnouncement(token, """{"placement":"modal","title":"리스트용 공지","isActive":true}""")
        mockMvc
            .get("/admin/announcements") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
    }

    @Test
    fun `운영자는 공지 제목을 수정할 수 있다`() {
        val token = adminToken()
        val id = createAnnouncement(token, """{"placement":"bar","title":"원본 제목"}""")
        mockMvc
            .patch("/admin/announcements/$id") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"수정됨"}"""
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.title") { value("수정됨") } }
    }

    @Test
    fun `운영자는 공지를 숨김 처리하면 isActive 가 false 가 된다`() {
        val token = adminToken()
        val id = createAnnouncement(token, """{"placement":"modal","title":"토글 공지","isActive":true}""")
        mockMvc
            .post("/admin/announcements/$id/active") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"active":false}"""
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.isActive") { value(false) } }
    }

    @Test
    fun `운영자는 공지를 다시 노출하면 isActive 가 true 가 된다`() {
        val token = adminToken()
        val id = createAnnouncement(token, """{"placement":"modal","title":"토글 공지"}""")
        mockMvc
            .post("/admin/announcements/$id/active") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"active":true}"""
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.isActive") { value(true) } }
    }

    @Test
    fun `잘못된 placement 로 생성하면 400`() {
        val token = adminToken()
        mockMvc
            .post("/admin/announcements") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"placement":"popup","title":"잘못된 위치"}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `비운영자가 공지를 생성하면 403`() {
        val token = userToken()
        mockMvc
            .post("/admin/announcements") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"placement":"modal","title":"권한 없음"}"""
            }.andExpect { status { isForbidden() } }
    }

    // ─────────────────────────── 점주 노출/클릭 ───────────────────────────

    @Test
    fun `점주는 활성 공지 목록을 받는다`() {
        val admin = adminToken()
        createAnnouncement(admin, """{"placement":"modal","title":"노출 공지","isActive":true}""")

        val user = userToken()
        mockMvc
            .get("/announcements") { header(HttpHeaders.AUTHORIZATION, "Bearer $user") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
    }

    @Test
    fun `점주는 placement 필터로 활성 공지를 받는다`() {
        val admin = adminToken()
        createAnnouncement(admin, """{"placement":"modal","title":"모달 공지","isActive":true}""")

        val user = userToken()
        mockMvc
            .get("/announcements?placement=modal") { header(HttpHeaders.AUTHORIZATION, "Bearer $user") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
    }

    @Test
    fun `점주 클릭은 204 이며 clickCount 가 증가한다`() {
        val admin = adminToken()
        val id = createAnnouncement(admin, """{"placement":"modal","title":"클릭 공지","isActive":true}""")

        val user = userToken()
        mockMvc
            .post("/announcements/$id/click") { header(HttpHeaders.AUTHORIZATION, "Bearer $user") }
            .andExpect { status { isNoContent() } }

        // clickCount 가 1 이상으로 증가했는지 영속 상태로 검증(목록 JSONPath 필터는 불안정).
        assertThat(announcementRepository.findById(id).orElseThrow().clickCount).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `삭제된 공지는 점주 목록에 더 이상 노출되지 않는다`() {
        val admin = adminToken()
        val id = createAnnouncement(admin, """{"placement":"modal","title":"삭제될 공지","isActive":true}""")

        mockMvc
            .delete("/admin/announcements/$id") { header(HttpHeaders.AUTHORIZATION, "Bearer $admin") }
            .andExpect { status { isNoContent() } }

        val user = userToken()
        mockMvc
            .get("/announcements") { header(HttpHeaders.AUTHORIZATION, "Bearer $user") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$[?(@.id == $id)]") { isEmpty() } }
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** 공지를 생성하고 생성된 id 를 반환한다. */
    private fun createAnnouncement(
        token: String,
        body: String,
    ): Long {
        val response =
            mockMvc
                .post("/admin/announcements") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = body
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

    private companion object {
        val ID_REGEX = Regex("\"id\"\\s*:\\s*(\\d+)")
    }
}
