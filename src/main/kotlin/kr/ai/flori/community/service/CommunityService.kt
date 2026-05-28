package kr.ai.flori.community.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.community.domain.CommunityCategories
import kr.ai.flori.community.dto.CommentCreateRequest
import kr.ai.flori.community.dto.CommentResponse
import kr.ai.flori.community.dto.CommunityFileMetaRequest
import kr.ai.flori.community.dto.CommunityUploadTargetResponse
import kr.ai.flori.community.dto.LikeToggleResponse
import kr.ai.flori.community.dto.PostCreateRequest
import kr.ai.flori.community.dto.PostResponse
import kr.ai.flori.community.dto.PostUpdateRequest
import kr.ai.flori.community.dto.PostsPageResponse
import kr.ai.flori.community.entity.CommunityComment
import kr.ai.flori.community.entity.CommunityLike
import kr.ai.flori.community.entity.CommunityPost
import kr.ai.flori.community.error.CommunityErrorCode
import kr.ai.flori.community.repository.CommunityCommentRepository
import kr.ai.flori.community.repository.CommunityLikeRepository
import kr.ai.flori.community.repository.CommunityPostRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 커뮤니티 서비스. 단일 커뮤니티(테넌트 간 공유)이므로 user_id 격리 대상이 아니다 —
 * 신원/권한/마스킹은 뷰어(JWT) + author_user_id 로 서버가 계산한다.
 *
 * - 비밀글/비밀댓글 열람 권한은 canView로 계산해 응답에 포함하고, 비권한자에겐 본문을 비운다.
 * - 글 수정은 작성자만, 글/댓글 삭제는 작성자+관리자. 삭제는 soft delete.
 * - like_count/comment_count는 비정규화(원자적 증감).
 */
@Service
class CommunityService(
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository,
    private val likeRepository: CommunityLikeRepository,
    private val userRepository: UserRepository,
    private val s3PresignService: S3PresignService,
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
        val pageable = PageRequest.of(offset / limit, limit)
        val page = postRepository.findFeed(category?.takeIf { it.isNotBlank() }, searchPattern, pageable)
        val posts = page.content
        val nicknames = nicknamesOf(posts.map { it.authorUserId })
        val likedIds =
            if (posts.isEmpty()) {
                emptySet()
            } else {
                likeRepository.findLikedPostIds(viewer.id, posts.mapNotNull { it.id }).toSet()
            }
        val responses =
            posts.map { post ->
                PostResponse.of(
                    post = post,
                    authorNickname = nicknames[post.authorUserId] ?: UNKNOWN_NICKNAME,
                    liked = post.id in likedIds,
                    isMine = post.authorUserId == viewer.id,
                    canView = canViewPost(post, viewer),
                )
            }
        return PostsPageResponse(responses, page.hasNext())
    }

    @Transactional(readOnly = true)
    fun getPost(id: Long): PostResponse {
        val viewer = viewer()
        val post = loadPost(id)
        return PostResponse.of(
            post = post,
            authorNickname = nicknameOf(post.authorUserId),
            liked = likeRepository.existsByPostIdAndUserId(id, viewer.id),
            isMine = post.authorUserId == viewer.id,
            canView = canViewPost(post, viewer),
        )
    }

    @Transactional
    fun createPost(request: PostCreateRequest): PostResponse {
        val viewer = viewer()
        val category = requireCategory(requireNotNull(request.category), viewer)
        val post =
            CommunityPost(
                authorUserId = viewer.id,
                category = category,
                title = requireNotNull(request.title),
            )
        post.content = requireNotNull(request.contentJson)
        post.contentText = request.contentText
        post.isSecret = request.isSecret
        post.imageUrls = request.imageUrls.toTypedArray()
        val saved = postRepository.save(post)
        return PostResponse.of(saved, nicknameOf(viewer.id), liked = false, isMine = true, canView = true)
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
        request.contentJson?.let { post.content = it }
        request.contentText?.let { post.contentText = it }
        request.isSecret?.let { post.isSecret = it }
        request.imageUrls?.let { post.imageUrls = it.toTypedArray() }
        val saved = postRepository.save(post)
        return PostResponse.of(
            post = saved,
            authorNickname = nicknameOf(saved.authorUserId),
            liked = likeRepository.existsByPostIdAndUserId(id, viewer.id),
            isMine = true,
            canView = true,
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

    // ── 업로드 타깃(presigned) ───────────────────────────────────────────

    fun createUploadTargets(files: List<CommunityFileMetaRequest>): List<CommunityUploadTargetResponse> {
        val userId = TenantContext.currentUserId()
        if (files.size > MAX_FILES_PER_REQUEST) {
            throw AppException(CommonErrorCode.VALIDATION, "한 번에 최대 ${MAX_FILES_PER_REQUEST}장까지 업로드할 수 있습니다")
        }
        return files.map { file ->
            val contentType = requireNotNull(file.type)
            validateImageMeta(contentType, file.size)
            val name = requireNotNull(file.name)
            val presigned = s3PresignService.presignUpload(buildKey(userId, name), contentType)
            CommunityUploadTargetResponse(presigned.uploadUrl, presigned.fileUrl, name)
        }
    }

    // ── 댓글 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listComments(postId: Long): List<CommentResponse> {
        val viewer = viewer()
        val post = loadPost(postId)
        val comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
        val nicknames = nicknamesOf(comments.map { it.authorUserId })
        val authorById = comments.mapNotNull { c -> c.id?.let { it to c.authorUserId } }.toMap()
        return comments.map { comment ->
            val parentAuthorId = comment.parentId?.let { authorById[it] }
            CommentResponse.of(
                comment = comment,
                authorNickname = nicknames[comment.authorUserId] ?: UNKNOWN_NICKNAME,
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
        loadPost(postId)
        var secret = request.isSecret
        request.parentId?.let { parentId ->
            // 대댓글 부모 검증: 같은 글의 미삭제 댓글 + 깊이 +1이 MAX_COMMENT_DEPTH 이내(루트=1). 위반 시 INVALID_PARENT.
            val parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
            var depth = 1
            var ancestorId = parent?.parentId
            while (ancestorId != null) {
                depth++
                ancestorId = commentRepository.findById(ancestorId).orElse(null)?.parentId
            }
            if (parent == null || parent.postId != postId || depth + 1 > MAX_COMMENT_DEPTH) {
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
        return CommentResponse.of(saved, nicknameOf(viewer.id), isMine = true, canView = true)
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

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────

    private fun viewer(): Viewer {
        val id = TenantContext.currentUserId()
        val user = userRepository.findById(id).orElseThrow { AppException(CommunityErrorCode.FORBIDDEN) }
        return Viewer(id, user.isAdmin)
    }

    private fun loadPost(id: Long): CommunityPost =
        postRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw AppException(CommunityErrorCode.POST_NOT_FOUND)

    private fun canViewPost(
        post: CommunityPost,
        viewer: Viewer,
    ): Boolean = !post.isSecret || post.authorUserId == viewer.id || viewer.isAdmin

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

    private fun nicknameOf(userId: Long): String = nicknamesOf(listOf(userId))[userId] ?: UNKNOWN_NICKNAME

    private fun nicknamesOf(userIds: Collection<Long>): Map<Long, String> {
        if (userIds.isEmpty()) return emptyMap()
        return userRepository
            .findAllById(userIds.toSet())
            .associate { requireNotNull(it.id) to it.nickname }
    }

    private fun validateImageMeta(
        contentType: String,
        size: Long,
    ) {
        // SVG 등 스크립트 내장 가능 타입 차단을 위해 prefix가 아닌 명시 허용 목록으로 검증.
        if (contentType.lowercase() !in ALLOWED_IMAGE_TYPES) {
            throw AppException(CommonErrorCode.VALIDATION, "지원하지 않는 이미지 형식입니다")
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            throw AppException(CommonErrorCode.VALIDATION, "파일 크기가 너무 큽니다")
        }
    }

    private fun buildKey(
        userId: Long,
        name: String,
    ): String {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "community/$userId/${UUID.randomUUID()}-$safeName"
    }

    private companion object {
        const val UNKNOWN_NICKNAME = "알 수 없음"
        const val MAX_COMMENT_DEPTH = 5
        const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
        const val MAX_FILES_PER_REQUEST = 10
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/heic")
    }
}
