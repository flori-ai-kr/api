package kr.ai.flori.community.service

import com.fasterxml.jackson.databind.JsonNode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.community.domain.CommunityCategories
import kr.ai.flori.community.domain.CommunityReportReasons
import kr.ai.flori.community.dto.CommentCreateRequest
import kr.ai.flori.community.dto.CommentResponse
import kr.ai.flori.community.dto.LikeToggleResponse
import kr.ai.flori.community.dto.PostCreateRequest
import kr.ai.flori.community.dto.PostResponse
import kr.ai.flori.community.dto.PostUpdateRequest
import kr.ai.flori.community.dto.PostsPageResponse
import kr.ai.flori.community.dto.ReportCreateRequest
import kr.ai.flori.community.entity.CommunityComment
import kr.ai.flori.community.entity.CommunityLike
import kr.ai.flori.community.entity.CommunityPost
import kr.ai.flori.community.entity.CommunityReport
import kr.ai.flori.community.error.CommunityErrorCode
import kr.ai.flori.community.repository.CommunityBanRepository
import kr.ai.flori.community.repository.CommunityCommentRepository
import kr.ai.flori.community.repository.CommunityLikeRepository
import kr.ai.flori.community.repository.CommunityPostRepository
import kr.ai.flori.community.repository.CommunityReportRepository
import kr.ai.flori.user.entity.User
import kr.ai.flori.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 커뮤니티 서비스. 단일 커뮤니티(테넌트 간 공유)이므로 user_id 격리 대상이 아니다 —
 * 신원/권한/마스킹은 뷰어(JWT) + author_user_id 로 서버가 계산한다.
 *
 * - 비밀댓글 열람 권한은 canView로 계산해 응답에 포함하고, 비권한자에겐 본문을 비운다.
 * - 글 수정은 작성자만, 글/댓글 삭제는 작성자+관리자. 삭제는 soft delete.
 * - like_count/comment_count는 비정규화(원자적 증감).
 */
@Service
@Suppress("TooManyFunctions")
class CommunityService(
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository,
    private val likeRepository: CommunityLikeRepository,
    private val reportRepository: CommunityReportRepository,
    private val banRepository: CommunityBanRepository,
    private val userRepository: UserRepository,
) {
    private data class Viewer(
        val id: Long,
        val isAdmin: Boolean,
    )

    // ── 게시글 ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listPosts(
        category: String?,
        search: String?,
        offset: Int,
        limit: Int,
    ): PostsPageResponse {
        val viewer = viewer()
        val searchPattern = search?.takeIf { it.isNotBlank() }?.let { "%${it.lowercase()}%" }
        val pageable = Paging.offsetLimit(offset, limit, MAX_LIMIT)
        val page = postRepository.findFeed(category?.takeIf { it.isNotBlank() }, searchPattern, pageable)
        val posts = page.content
        val authors = authorsOf(posts.map { it.authorUserId })
        val likedIds =
            if (posts.isEmpty()) {
                emptySet()
            } else {
                likeRepository.findLikedPostIds(viewer.id, posts.mapNotNull { it.id }).toSet()
            }
        val responses =
            posts.map { post ->
                val author = authors[post.authorUserId]
                PostResponse.of(
                    post = post,
                    authorNickname = author?.nickname ?: UNKNOWN_NICKNAME,
                    authorIsAdmin = author?.isAdmin ?: false,
                    liked = post.id in likedIds,
                    isMine = post.authorUserId == viewer.id,
                    viewerIsAdmin = viewer.isAdmin,
                )
            }
        return PostsPageResponse(responses, page.hasNext())
    }

    @Transactional(readOnly = true)
    fun getPost(id: Long): PostResponse {
        val viewer = viewer()
        val post = loadPost(id)
        val author = authorsOf(listOf(post.authorUserId))[post.authorUserId]
        return PostResponse.of(
            post = post,
            authorNickname = author?.nickname ?: UNKNOWN_NICKNAME,
            authorIsAdmin = author?.isAdmin ?: false,
            liked = likeRepository.existsByPostIdAndUserId(id, viewer.id),
            isMine = post.authorUserId == viewer.id,
            viewerIsAdmin = viewer.isAdmin,
        )
    }

    @Transactional
    fun createPost(request: PostCreateRequest): PostResponse {
        val viewer = viewer()
        requireNotBanned(viewer.id)
        val category = requireCategory(requireNotNull(request.category), viewer)
        val post =
            CommunityPost(
                authorUserId = viewer.id,
                category = category,
                title = requireNotNull(request.title),
            )
        post.content = requireValidContent(requireNotNull(request.contentJson))
        post.contentText = request.contentText
        post.imageUrls = request.imageUrls.toTypedArray()
        val saved = postRepository.save(post)
        return PostResponse.of(
            saved,
            nicknameOf(viewer.id),
            authorIsAdmin = viewer.isAdmin,
            liked = false,
            isMine = true,
            viewerIsAdmin = viewer.isAdmin,
        )
    }

    @Transactional
    fun updatePost(
        id: Long,
        request: PostUpdateRequest,
    ): PostResponse {
        val viewer = viewer()
        val post = loadPost(id)
        // 수정은 작성자만(관리자도 타인 글 수정 불가).
        if (post.authorUserId != viewer.id) throw AppException(CommunityErrorCode.FORBIDDEN)
        request.category?.let { post.category = requireCategory(it, viewer) }
        request.title?.let { post.title = it }
        request.contentJson?.let { post.content = requireValidContent(it) }
        request.contentText?.let { post.contentText = it }
        request.imageUrls?.let { post.imageUrls = it.toTypedArray() }
        val saved = postRepository.save(post)
        return PostResponse.of(
            post = saved,
            authorNickname = nicknameOf(saved.authorUserId),
            authorIsAdmin = viewer.isAdmin,
            liked = likeRepository.existsByPostIdAndUserId(id, viewer.id),
            isMine = true,
            viewerIsAdmin = viewer.isAdmin,
        )
    }

    @Transactional
    fun deletePost(id: Long) {
        val viewer = viewer()
        val post = loadPost(id)
        if (post.authorUserId != viewer.id && !viewer.isAdmin) throw AppException(CommunityErrorCode.FORBIDDEN)
        post.deletedAt = Instant.now()
        postRepository.save(post)
    }

    /** 게시글 고정/해제 — 관리자만. 모든 글에 적용 가능(작성자 무관). */
    @Transactional
    fun setPinned(
        id: Long,
        pinned: Boolean,
    ): PostResponse {
        val viewer = viewer()
        if (!viewer.isAdmin) throw AppException(CommunityErrorCode.PIN_ADMIN_ONLY)
        val post = loadPost(id)
        post.isPinned = pinned
        val saved = postRepository.save(post)
        val author = authorsOf(listOf(saved.authorUserId))[saved.authorUserId]
        return PostResponse.of(
            post = saved,
            authorNickname = author?.nickname ?: UNKNOWN_NICKNAME,
            authorIsAdmin = author?.isAdmin ?: false,
            liked = likeRepository.existsByPostIdAndUserId(id, viewer.id),
            isMine = saved.authorUserId == viewer.id,
            viewerIsAdmin = viewer.isAdmin,
        )
    }

    @Transactional
    fun toggleLike(id: Long): LikeToggleResponse {
        val viewer = viewer()
        loadPost(id)
        val existing = likeRepository.findByPostIdAndUserId(id, viewer.id)
        val liked: Boolean
        if (existing != null) {
            likeRepository.delete(existing)
            postRepository.adjustLikeCount(id, -1)
            liked = false
        } else {
            likeRepository.save(CommunityLike(postId = id, userId = viewer.id))
            postRepository.adjustLikeCount(id, 1)
            liked = true
        }
        // 비정규화 카운트는 DB 원자 증감 후 재조회(동시성 시 정확한 값 반환). adjust 쿼리는 clearAutomatically로 컨텍스트를 비운다.
        val likeCount = postRepository.findByIdAndDeletedAtIsNull(id)?.likeCount ?: 0
        return LikeToggleResponse(liked = liked, likeCount = likeCount)
    }

    // ── 댓글 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listComments(postId: Long): List<CommentResponse> {
        val viewer = viewer()
        val post = loadPost(postId)
        val comments = commentRepository.findByPostIdAndHiddenAtIsNullOrderByCreatedAtAsc(postId)
        val authors = authorsOf(comments.map { it.authorUserId })
        val authorById = comments.mapNotNull { c -> c.id?.let { it to c.authorUserId } }.toMap()
        return comments.map { comment ->
            val parentAuthorId = comment.parentId?.let { authorById[it] }
            val author = authors[comment.authorUserId]
            CommentResponse.of(
                comment = comment,
                authorNickname = author?.nickname ?: UNKNOWN_NICKNAME,
                authorIsAdmin = author?.isAdmin ?: false,
                isMine = comment.authorUserId == viewer.id,
                canView = canViewComment(comment, post.authorUserId, parentAuthorId, viewer),
            )
        }
    }

    @Transactional
    fun createComment(
        postId: Long,
        request: CommentCreateRequest,
    ): CommentResponse {
        val viewer = viewer()
        requireNotBanned(viewer.id)
        loadPost(postId)
        var secret = request.isSecret
        request.parentId?.let { parentId ->
            // 대댓글 부모 검증: 같은 글의 미삭제 댓글 + 깊이 +1이 MAX_COMMENT_DEPTH 이내(루트=1). 위반 시 INVALID_PARENT.
            // 깊이는 단일 재귀 CTE(ancestorDepth)로 계산 — 조상 단건 반복조회(N+1) 제거.
            val parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
            if (parent == null ||
                parent.postId != postId ||
                commentRepository.ancestorDepth(parentId) + 1 > MAX_COMMENT_DEPTH
            ) {
                throw AppException(CommunityErrorCode.INVALID_PARENT)
            }
            // 부모가 비밀이면 자식도 비밀 강제.
            if (parent.isSecret) secret = true
        }
        val comment =
            CommunityComment(
                postId = postId,
                authorUserId = viewer.id,
                content = requireNotNull(request.content),
            )
        comment.parentId = request.parentId
        comment.isSecret = secret
        val saved = commentRepository.save(comment)
        postRepository.adjustCommentCount(postId, 1)
        return CommentResponse.of(saved, nicknameOf(viewer.id), authorIsAdmin = viewer.isAdmin, isMine = true, canView = true)
    }

    @Transactional
    fun deleteComment(id: Long) {
        val viewer = viewer()
        val comment =
            commentRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw AppException(CommunityErrorCode.COMMENT_NOT_FOUND)
        if (comment.authorUserId != viewer.id && !viewer.isAdmin) throw AppException(CommunityErrorCode.FORBIDDEN)
        comment.deletedAt = Instant.now()
        commentRepository.save(comment)
        postRepository.adjustCommentCount(comment.postId, -1)
    }

    // ── 신고 ──────────────────────────────────────────────────────────────

    @Transactional
    fun reportPost(
        postId: Long,
        request: ReportCreateRequest,
    ) = report(CommunityReport.TARGET_POST, postId, request) { loadPost(postId) }

    @Transactional
    fun reportComment(
        commentId: Long,
        request: ReportCreateRequest,
    ) = report(CommunityReport.TARGET_COMMENT, commentId, request) {
        commentRepository.findByIdAndDeletedAtIsNull(commentId)
            ?: throw AppException(CommunityErrorCode.COMMENT_NOT_FOUND)
    }

    // 신고 공통: 대상 존재 확인 → 사유 검증 → 중복이면 멱등 no-op → 저장.
    private fun report(
        targetType: String,
        targetId: Long,
        request: ReportCreateRequest,
        loadTarget: () -> Unit,
    ) {
        val reporterId = TenantContext.currentUserId()
        loadTarget()
        val reason = requireReason(requireNotNull(request.reason))
        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterUserId(targetType, targetId, reporterId)) {
            return
        }
        val entity =
            CommunityReport(
                targetType = targetType,
                targetId = targetId,
                reporterUserId = reporterId,
                reason = reason,
            )
        entity.detail = request.detail
        reportRepository.save(entity)
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────

    private fun requireReason(reason: String): String {
        if (reason !in CommunityReportReasons.ALL) throw AppException(CommunityErrorCode.INVALID_REASON)
        return reason
    }

    // 활성 차단(미해제 + 미만료) 사용자는 글/댓글 작성 금지.
    private fun requireNotBanned(userId: Long) {
        val ban = banRepository.findByUserIdAndLiftedAtIsNull(userId) ?: return
        if (ban.isActive()) throw AppException(CommunityErrorCode.BANNED)
    }

    private fun viewer(): Viewer {
        val id = TenantContext.currentUserId()
        val user = userRepository.findById(id).orElseThrow { AppException(CommunityErrorCode.FORBIDDEN) }
        return Viewer(id, user.isAdmin)
    }

    // 일반 사용자 경로는 삭제뿐 아니라 운영자 숨김 글도 404 처리(노출 제외와 동일한 의미).
    private fun loadPost(id: Long): CommunityPost =
        postRepository.findByIdAndDeletedAtIsNullAndHiddenAtIsNull(id)
            ?: throw AppException(CommunityErrorCode.POST_NOT_FOUND)

    private fun canViewComment(
        comment: CommunityComment,
        postAuthorId: Long,
        parentAuthorId: Long?,
        viewer: Viewer,
    ): Boolean {
        if (!comment.isSecret) return true
        if (viewer.isAdmin) return true
        return viewer.id == comment.authorUserId ||
            viewer.id == postAuthorId ||
            (parentAuthorId != null && viewer.id == parentAuthorId)
    }

    private fun requireCategory(
        category: String,
        viewer: Viewer,
    ): String {
        if (category !in CommunityCategories.ALL) throw AppException(CommunityErrorCode.INVALID_CATEGORY)
        if (category in CommunityCategories.ADMIN_ONLY && !viewer.isAdmin) {
            throw AppException(CommunityErrorCode.NOTICE_ADMIN_ONLY)
        }
        return category
    }

    private fun nicknameOf(userId: Long): String = authorsOf(listOf(userId))[userId]?.nickname ?: UNKNOWN_NICKNAME

    private fun authorsOf(userIds: Collection<Long>): Map<Long, User> {
        if (userIds.isEmpty()) return emptyMap()
        return userRepository
            .findAllById(userIds.toSet())
            .associateBy { requireNotNull(it.id) }
    }

    private companion object {
        const val MAX_LIMIT = 100
        const val UNKNOWN_NICKNAME = "알 수 없음"
        const val MAX_COMMENT_DEPTH = 5
    }
}

/** jsonb 본문(content)의 직렬화 크기 상한 검증 — Bean Validation으로 못 거르는 거대 페이로드를 400으로 차단. */
private fun requireValidContent(content: JsonNode): JsonNode {
    if (content.toString().length > FieldLimits.CONTENT_JSON) {
        throw AppException(CommonErrorCode.VALIDATION, "본문이 너무 깁니다")
    }
    return content
}
