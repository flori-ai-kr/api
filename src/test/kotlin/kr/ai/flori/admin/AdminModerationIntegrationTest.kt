package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.community.entity.CommunityComment
import kr.ai.flori.community.entity.CommunityPost
import kr.ai.flori.community.entity.CommunityReport
import kr.ai.flori.community.repository.CommunityBanRepository
import kr.ai.flori.community.repository.CommunityCommentRepository
import kr.ai.flori.community.repository.CommunityPostRepository
import kr.ai.flori.community.repository.CommunityReportRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

/**
 * 운영 콘솔 커뮤니티 모더레이션 통합 테스트.
 *
 * 시드는 리포지토리로 엔티티를 직접 save 한다(서비스/REST 작성 경로를 거치지 않음):
 * - 게시글/댓글은 author_user_id 만 있으면 되므로 임의의 작성자 id(adminToken 발급 시 만든 유저 id 또는 9999L)를 사용.
 * - 신고는 CommunityReport(targetType, targetId, reporterUserId, reason) 생성자로 pending 상태로 생성.
 * - hiddenAt/hiddenBy/resolution 등은 protected set 이므로 검증 시 reload 후 getter 로만 확인.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var postRepository: CommunityPostRepository

    @Autowired private lateinit var commentRepository: CommunityCommentRepository

    @Autowired private lateinit var reportRepository: CommunityReportRepository

    @Autowired private lateinit var banRepository: CommunityBanRepository

    // ── 신고 큐 ──────────────────────────────────────────────────────────

    @Test
    fun `운영자는 pending 신고 큐를 받는다 — targetPreview·reportCount 포함`() {
        val token = adminToken()
        val post = seedPost(title = "신고 대상 글", text = "미리보기 본문")
        seedReport(targetId = post.id!!)

        mockMvc
            .get("/admin/community/reports?status=pending") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }
            .andExpect { jsonPath("$[0].targetPreview") { exists() } }
            .andExpect { jsonPath("$[0].reportCount") { value(greaterThanOrEqualTo(1)) } }
    }

    @Test
    fun `신고를 hidden 으로 처리하면 게시글이 숨김되고 신고는 resolved`() {
        val token = adminToken()
        val post = seedPost()
        val report = seedReport(targetId = post.id!!)

        mockMvc
            .post("/admin/community/reports/${report.id}/resolve") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"resolution":"hidden"}"""
            }.andExpect { status { is2xxSuccessful() } }

        assertNotNull(postRepository.findById(post.id!!).orElseThrow().hiddenAt)
        val reloaded = reportRepository.findById(report.id!!).orElseThrow()
        kotlin.test.assertEquals(CommunityReport.STATUS_RESOLVED, reloaded.status)
    }

    @Test
    fun `신고를 deleted 로 처리하면 게시글이 삭제(soft delete)된다`() {
        val token = adminToken()
        val post = seedPost()
        val report = seedReport(targetId = post.id!!)

        mockMvc
            .post("/admin/community/reports/${report.id}/resolve") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"resolution":"deleted"}"""
            }.andExpect { status { is2xxSuccessful() } }

        assertNotNull(postRepository.findById(post.id!!).orElseThrow().deletedAt)
    }

    @Test
    fun `신고를 dismissed 로 처리하면 대상은 그대로이고 신고만 기각된다`() {
        val token = adminToken()
        val post = seedPost()
        val report = seedReport(targetId = post.id!!)

        mockMvc
            .post("/admin/community/reports/${report.id}/resolve") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"resolution":"dismissed"}"""
            }.andExpect { status { is2xxSuccessful() } }

        val reloadedPost = postRepository.findById(post.id!!).orElseThrow()
        assertNull(reloadedPost.hiddenAt)
        assertNull(reloadedPost.deletedAt)
        kotlin.test.assertEquals(CommunityReport.STATUS_DISMISSED, reportRepository.findById(report.id!!).orElseThrow().status)
    }

    @Test
    fun `알 수 없는 resolution 값은 400`() {
        val token = adminToken()
        val post = seedPost()
        val report = seedReport(targetId = post.id!!)

        mockMvc
            .post("/admin/community/reports/${report.id}/resolve") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"resolution":"bogus"}"""
            }.andExpect { status { isBadRequest() } }
    }

    // ── 게시글 모더레이션 ─────────────────────────────────────────────────

    @Test
    fun `게시글 hide 후 unhide 하면 hiddenAt 이 설정됐다가 해제된다`() {
        val token = adminToken()
        val post = seedPost()

        mockMvc
            .post("/admin/community/posts/${post.id}/hide") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { is2xxSuccessful() } }
        assertNotNull(postRepository.findById(post.id!!).orElseThrow().hiddenAt)

        mockMvc
            .post("/admin/community/posts/${post.id}/unhide") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { is2xxSuccessful() } }
        assertNull(postRepository.findById(post.id!!).orElseThrow().hiddenAt)
    }

    @Test
    fun `게시글 삭제하면 deletedAt 이 설정된다`() {
        val token = adminToken()
        val post = seedPost()

        mockMvc
            .delete("/admin/community/posts/${post.id}") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { is2xxSuccessful() } }

        assertNotNull(postRepository.findById(post.id!!).orElseThrow().deletedAt)
    }

    // ── 댓글 모더레이션 ──────────────────────────────────────────────────

    @Test
    fun `댓글 hide 후 unhide 하면 hiddenAt 이 설정됐다가 해제된다`() {
        val token = adminToken()
        val post = seedPost()
        val comment = seedComment(postId = post.id!!)

        mockMvc
            .post("/admin/community/comments/${comment.id}/hide") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { is2xxSuccessful() } }
        assertNotNull(commentRepository.findById(comment.id!!).orElseThrow().hiddenAt)

        mockMvc
            .post("/admin/community/comments/${comment.id}/unhide") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { is2xxSuccessful() } }
        assertNull(commentRepository.findById(comment.id!!).orElseThrow().hiddenAt)
    }

    @Test
    fun `댓글 삭제하면 deletedAt 이 설정된다`() {
        val token = adminToken()
        val post = seedPost()
        val comment = seedComment(postId = post.id!!)

        mockMvc
            .delete("/admin/community/comments/${comment.id}") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { is2xxSuccessful() } }

        assertNotNull(commentRepository.findById(comment.id!!).orElseThrow().deletedAt)
    }

    // ── 차단(밴) ──────────────────────────────────────────────────────────

    @Test
    fun `차단 생성·목록·중복차단 409·해제 흐름`() {
        val token = adminToken()
        val targetUserId = 4242L

        // 생성 → 2xx
        mockMvc
            .post("/admin/community/bans") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"userId":$targetUserId,"reason":"스팸"}"""
            }.andExpect { status { is2xxSuccessful() } }

        // 목록 → length >= 1
        mockMvc
            .get("/admin/community/bans") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(greaterThanOrEqualTo(1)) } }

        // 동일 유저 재차단 → 409 (ALREADY_BANNED)
        mockMvc
            .post("/admin/community/bans") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"userId":$targetUserId,"reason":"스팸"}"""
            }.andExpect { status { isConflict() } }

        // 해제 → 2xx
        val ban = banRepository.findByUserIdAndLiftedAtIsNull(targetUserId)!!
        mockMvc
            .delete("/admin/community/bans/${ban.id}") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { is2xxSuccessful() } }

        assertNotNull(banRepository.findById(ban.id!!).orElseThrow().liftedAt)
    }

    // ── 권한 ──────────────────────────────────────────────────────────────

    @Test
    fun `비운영자는 신고 큐 조회가 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc
            .get("/admin/community/reports") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isForbidden() } }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private fun seedPost(
        authorUserId: Long = 9999L,
        title: String = "테스트 글",
        text: String = "테스트 본문",
    ): CommunityPost {
        val post =
            CommunityPost(
                authorUserId = authorUserId,
                category = "daily",
                title = title,
            )
        post.contentText = text
        return postRepository.save(post)
    }

    private fun seedComment(
        postId: Long,
        authorUserId: Long = 9999L,
        content: String = "테스트 댓글",
    ): CommunityComment =
        commentRepository.save(
            CommunityComment(
                postId = postId,
                authorUserId = authorUserId,
                content = content,
            ),
        )

    private fun seedReport(
        targetId: Long,
        targetType: String = CommunityReport.TARGET_POST,
        reporterUserId: Long = 8888L,
        reason: String = "spam",
    ): CommunityReport =
        reportRepository.save(
            CommunityReport(
                targetType = targetType,
                targetId = targetId,
                reporterUserId = reporterUserId,
                reason = reason,
            ),
        )

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }
}
