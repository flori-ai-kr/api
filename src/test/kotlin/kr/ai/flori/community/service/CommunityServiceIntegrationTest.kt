package kr.ai.flori.community.service

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.community.dto.CommentCreateRequest
import kr.ai.flori.community.dto.PostCreateRequest
import kr.ai.flori.community.dto.PostUpdateRequest
import kr.ai.flori.community.error.CommunityErrorCode
import kr.ai.flori.community.repository.CommunityCommentRepository
import kr.ai.flori.community.repository.CommunityLikeRepository
import kr.ai.flori.community.repository.CommunityPostRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class CommunityServiceIntegrationTest {
    @Autowired
    lateinit var communityService: CommunityService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var postRepository: CommunityPostRepository

    @Autowired
    lateinit var commentRepository: CommunityCommentRepository

    @Autowired
    lateinit var likeRepository: CommunityLikeRepository

    // 커뮤니티는 테넌트 전역 공유 테이블이라 테스트 간 데이터가 누적되면 목록/카운트 단언이 흔들린다.
    // 매 테스트 후 커뮤니티 데이터를 비워 메서드 실행 순서에 무관하게 격리한다.
    @AfterEach
    fun tearDown() {
        TenantContext.clear()
        likeRepository.deleteAll()
        commentRepository.deleteAll()
        postRepository.deleteAll()
    }

    private fun newUser(): Long {
        val email = "community-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        return requireNotNull(userRepository.findByEmail(email)).id!!
    }

    private fun switchTo(userId: Long) = TenantContext.set(userId)

    private fun makeAdmin(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
    }

    private fun content(text: String) = JsonNodeFactory.instance.objectNode().put("text", text)

    private fun post(
        category: String = "daily",
        title: String = "제목",
        secret: Boolean = false,
    ) = PostCreateRequest(
        category = category,
        title = title,
        contentJson = content("본문"),
        contentText = "본문",
        isSecret = secret,
        imageUrls = emptyList(),
    )

    @Test
    fun `거대한 본문(contentJson)은 직렬화 크기 상한으로 거부된다`() {
        val me = newUser()
        switchTo(me)
        val huge = post().copy(contentJson = content("가".repeat(100_001))) // FieldLimits.CONTENT_JSON 초과
        assertThatThrownBy { communityService.createPost(huge) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(kr.ai.flori.common.error.CommonErrorCode.VALIDATION)
            }
    }

    @Test
    fun `게시글 생성·조회는 작성자에게 isMine·canView true`() {
        val me = newUser()
        switchTo(me)
        val created = communityService.createPost(post())
        val fetched = communityService.getPost(created.id)

        assertThat(fetched.isMine).isTrue()
        assertThat(fetched.canView).isTrue()
        assertThat(fetched.contentText).isEqualTo("본문")
        assertThat(fetched.authorNickname).isNotBlank()
    }

    @Test
    fun `공지는 관리자만 작성 가능`() {
        val me = newUser()
        switchTo(me)
        assertThatThrownBy { communityService.createPost(post(category = "notice")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommunityErrorCode.NOTICE_ADMIN_ONLY)
            }

        makeAdmin(me)
        val notice = communityService.createPost(post(category = "notice", title = "공지사항"))
        assertThat(notice.category).isEqualTo("notice")
    }

    @Test
    fun `운영자가 쓴 글·댓글은 authorIsAdmin이 true`() {
        val admin = newUser()
        makeAdmin(admin)
        switchTo(admin)
        val created = communityService.createPost(post())
        assertThat(created.authorIsAdmin).isTrue()

        val comment = communityService.createComment(created.id, CommentCreateRequest("운영자 댓글", null, false))
        assertThat(comment.authorIsAdmin).isTrue()

        // 다른 사용자가 조회해도 작성자 운영자 여부는 그대로 노출된다
        val other = newUser()
        switchTo(other)
        assertThat(communityService.getPost(created.id).authorIsAdmin).isTrue()
        assertThat(communityService.listComments(created.id).first { it.id == comment.id }.authorIsAdmin).isTrue()
    }

    @Test
    fun `일반 점주가 쓴 글·댓글은 authorIsAdmin이 false`() {
        val me = newUser()
        switchTo(me)
        val created = communityService.createPost(post())
        assertThat(created.authorIsAdmin).isFalse()
        assertThat(communityService.getPost(created.id).authorIsAdmin).isFalse()

        val comment = communityService.createComment(created.id, CommentCreateRequest("일반 댓글", null, false))
        assertThat(comment.authorIsAdmin).isFalse()
        assertThat(communityService.listComments(created.id).first { it.id == comment.id }.authorIsAdmin).isFalse()
    }

    @Test
    fun `잘못된 카테고리는 거부된다`() {
        val me = newUser()
        switchTo(me)
        assertThatThrownBy { communityService.createPost(post(category = "invalid")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommunityErrorCode.INVALID_CATEGORY)
            }
    }

    @Test
    fun `비밀글은 비권한자에게 마스킹되고 작성자·관리자는 열람 가능`() {
        val author = newUser()
        switchTo(author)
        val secret = communityService.createPost(post(secret = true, title = "비밀"))

        // 타 사용자: canView=false, 본문 마스킹
        val other = newUser()
        switchTo(other)
        val masked = communityService.getPost(secret.id)
        assertThat(masked.canView).isFalse()
        assertThat(masked.contentText).isEmpty()
        assertThat(masked.title).isEqualTo("비밀") // 제목은 노출

        // 관리자: 열람 가능
        val admin = newUser()
        makeAdmin(admin)
        switchTo(admin)
        assertThat(communityService.getPost(secret.id).canView).isTrue()
    }

    @Test
    fun `수정은 작성자만, 삭제는 작성자+관리자`() {
        val author = newUser()
        switchTo(author)
        val p = communityService.createPost(post())

        val other = newUser()
        switchTo(other)
        assertThatThrownBy { communityService.updatePost(p.id, PostUpdateRequest(title = "해킹")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommunityErrorCode.FORBIDDEN)
            }
        // 관리자도 타인 글 수정 불가
        val admin = newUser()
        makeAdmin(admin)
        switchTo(admin)
        assertThatThrownBy { communityService.updatePost(p.id, PostUpdateRequest(title = "관리자수정")) }
            .isInstanceOf(AppException::class.java)
        // 관리자는 삭제 가능(soft)
        communityService.deletePost(p.id)
        switchTo(author)
        assertThatThrownBy { communityService.getPost(p.id) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommunityErrorCode.POST_NOT_FOUND)
            }
    }

    @Test
    fun `작성자는 자신의 글을 수정할 수 있다`() {
        val me = newUser()
        switchTo(me)
        val p = communityService.createPost(post())
        val updated = communityService.updatePost(p.id, PostUpdateRequest(title = "수정됨", isSecret = true))
        assertThat(updated.title).isEqualTo("수정됨")
        assertThat(updated.isSecret).isTrue()
    }

    @Test
    fun `좋아요 토글은 liked·likeCount를 반영한다`() {
        val author = newUser()
        switchTo(author)
        val p = communityService.createPost(post())

        val liker = newUser()
        switchTo(liker)
        val on = communityService.toggleLike(p.id)
        assertThat(on.liked).isTrue()
        assertThat(on.likeCount).isEqualTo(1)
        assertThat(communityService.getPost(p.id).liked).isTrue()

        val off = communityService.toggleLike(p.id)
        assertThat(off.liked).isFalse()
        assertThat(off.likeCount).isEqualTo(0)
        assertThat(communityService.getPost(p.id).likeCount).isEqualTo(0)
    }

    @Test
    fun `댓글 작성·삭제 시 comment_count가 갱신되고 삭제는 톰스톤으로 남는다`() {
        val author = newUser()
        switchTo(author)
        val p = communityService.createPost(post())

        val c1 = communityService.createComment(p.id, CommentCreateRequest("첫 댓글", null, false))
        assertThat(communityService.getPost(p.id).commentCount).isEqualTo(1)

        communityService.deleteComment(c1.id)
        assertThat(communityService.getPost(p.id).commentCount).isEqualTo(0)

        val comments = communityService.listComments(p.id)
        assertThat(comments).hasSize(1)
        assertThat(comments.first().isDeleted).isTrue()
        assertThat(comments.first().content).isEmpty()
    }

    @Test
    fun `비밀댓글은 글작성자·댓글작성자·관리자에게만 보이고 부모가 비밀이면 자식도 비밀 강제`() {
        val author = newUser()
        switchTo(author)
        val p = communityService.createPost(post())

        val commenter = newUser()
        switchTo(commenter)
        val secretComment = communityService.createComment(p.id, CommentCreateRequest("비밀댓글", null, true))
        // 부모가 비밀 → 자식도 비밀 강제
        val reply = communityService.createComment(p.id, CommentCreateRequest("대댓글", secretComment.id, false))
        assertThat(reply.isSecret).isTrue()

        // 무관한 제3자: 비밀댓글 마스킹
        val stranger = newUser()
        switchTo(stranger)
        val visibleToStranger = communityService.listComments(p.id)
        val maskedComment = visibleToStranger.first { it.id == secretComment.id }
        assertThat(maskedComment.canView).isFalse()
        assertThat(maskedComment.content).isEmpty()

        // 글 작성자: 비밀댓글 열람 가능
        switchTo(author)
        val visibleToAuthor = communityService.listComments(p.id)
        assertThat(visibleToAuthor.first { it.id == secretComment.id }.canView).isTrue()
    }

    @Test
    fun `댓글 깊이는 최대 5단계까지 허용되고 초과하면 거부된다`() {
        val me = newUser()
        switchTo(me)
        val p = communityService.createPost(post())
        // 루트(depth 1) → 5단계까지 대댓글 허용
        var parentId: Long? = null
        val ids = mutableListOf<Long>()
        repeat(5) { i ->
            val c = communityService.createComment(p.id, CommentCreateRequest("depth${i + 1}", parentId, false))
            ids += c.id
            parentId = c.id
        }
        assertThat(ids).hasSize(5)

        // 6단계째는 거부
        assertThatThrownBy { communityService.createComment(p.id, CommentCreateRequest("depth6", parentId, false)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommunityErrorCode.INVALID_PARENT)
            }
    }

    @Test
    fun `다른 글의 댓글에는 대댓글을 달 수 없다`() {
        val me = newUser()
        switchTo(me)
        val p1 = communityService.createPost(post())
        val p2 = communityService.createPost(post())
        val c1 = communityService.createComment(p1.id, CommentCreateRequest("p1 댓글", null, false))

        assertThatThrownBy { communityService.createComment(p2.id, CommentCreateRequest("교차", c1.id, false)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommunityErrorCode.INVALID_PARENT)
            }
    }

    @Test
    fun `목록은 고정글 우선·최신순이며 페이지네이션을 지원한다`() {
        val me = newUser()
        switchTo(me)
        communityService.createPost(post(title = "A"))
        communityService.createPost(post(title = "B"))
        communityService.createPost(post(title = "C"))

        val firstPage = communityService.listPosts(null, null, 0, 2)
        assertThat(firstPage.posts).hasSize(2)
        assertThat(firstPage.hasMore).isTrue()
        // 최신순: 마지막 작성(C)이 먼저
        assertThat(firstPage.posts.first().title).isEqualTo("C")

        val secondPage = communityService.listPosts(null, null, 2, 2)
        assertThat(secondPage.posts).hasSize(1)
        assertThat(secondPage.hasMore).isFalse()
    }
}
