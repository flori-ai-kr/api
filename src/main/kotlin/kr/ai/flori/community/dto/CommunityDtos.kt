package kr.ai.flori.community.dto

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.community.entity.CommunityComment
import kr.ai.flori.community.entity.CommunityPost
import java.time.Instant

// ── 요청 ──────────────────────────────────────────────────────────────────

data class PostCreateRequest(
    @field:NotBlank(message = "카테고리는 필수입니다")
    val category: String?,
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = FieldLimits.TITLE, message = "제목이 너무 깁니다")
    val title: String?,
    @field:NotNull(message = "본문(contentJson)은 필수입니다")
    val contentJson: JsonNode?,
    @field:Size(max = FieldLimits.CONTENT_TEXT, message = "본문이 너무 깁니다")
    val contentText: String = "",
    val isSecret: Boolean = false,
    @field:Size(max = FieldLimits.IMAGE_COUNT, message = "이미지가 너무 많습니다")
    val imageUrls: List<
        @Size(max = FieldLimits.IMAGE_URL, message = "이미지 URL이 너무 깁니다")
        String,
    > = emptyList(),
)

/** 게시글 부분 수정. 제공된(non-null) 필드만 반영. */
data class PostUpdateRequest(
    val category: String? = null,
    @field:Size(max = FieldLimits.TITLE, message = "제목이 너무 깁니다")
    val title: String? = null,
    val contentJson: JsonNode? = null,
    @field:Size(max = FieldLimits.CONTENT_TEXT, message = "본문이 너무 깁니다")
    val contentText: String? = null,
    val isSecret: Boolean? = null,
    @field:Size(max = FieldLimits.IMAGE_COUNT, message = "이미지가 너무 많습니다")
    val imageUrls: List<
        @Size(max = FieldLimits.IMAGE_URL, message = "이미지 URL이 너무 깁니다")
        String,
    >? = null,
)

/** 게시글 고정/해제(관리자 전용). */
data class PinRequest(
    @field:NotNull(message = "pinned는 필수입니다")
    val pinned: Boolean?,
)

data class CommentCreateRequest(
    @field:NotBlank(message = "내용은 필수입니다")
    @field:Size(max = FieldLimits.COMMENT, message = "댓글이 너무 깁니다")
    val content: String?,
    val parentId: Long? = null,
    val isSecret: Boolean = false,
)

/** 신고 생성. reason은 화이트리스트 검증(서비스), detail은 선택 상세 설명. */
data class ReportCreateRequest(
    @field:NotBlank(message = "신고 사유는 필수입니다")
    val reason: String?,
    @field:Size(max = FieldLimits.CONTENT_TEXT, message = "상세 설명이 너무 깁니다")
    val detail: String? = null,
)

data class CommunityFileMetaRequest(
    @field:NotBlank val name: String?,
    @field:NotBlank val type: String?,
    val size: Long = 0,
)

data class CommunityUploadTargetsRequest(
    @field:NotEmpty(message = "파일 메타가 필요합니다")
    val files: List<CommunityFileMetaRequest>?,
)

// ── 응답 ──────────────────────────────────────────────────────────────────

data class CommunityUploadTargetResponse(
    val uploadUrl: String,
    val fileUrl: String,
    val originalName: String,
)

data class LikeToggleResponse(
    val liked: Boolean,
    val likeCount: Int,
)

data class PostsPageResponse(
    val posts: List<PostResponse>,
    val hasMore: Boolean,
)

/**
 * 게시글 응답(camelCase). 권한/마스킹은 서버가 뷰어(JWT) 기준으로 계산해 채운다.
 * canView=false(비권한 비밀글)면 content/contentText/imageUrls를 비운다.
 */
data class PostResponse(
    val id: Long,
    val authorNickname: String,
    val authorIsAdmin: Boolean,
    val category: String,
    val title: String,
    val content: JsonNode,
    val contentText: String,
    val imageUrls: List<String>,
    val isSecret: Boolean,
    val isPinned: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val liked: Boolean,
    val isMine: Boolean,
    // 뷰어(JWT)가 관리자인지 — 고정/해제 등 관리자 액션 노출 판단용.
    val viewerIsAdmin: Boolean,
    val canView: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(
            post: CommunityPost,
            authorNickname: String,
            authorIsAdmin: Boolean,
            liked: Boolean,
            isMine: Boolean,
            canView: Boolean,
            viewerIsAdmin: Boolean = false,
        ): PostResponse =
            PostResponse(
                id = requireNotNull(post.id),
                authorNickname = authorNickname,
                authorIsAdmin = authorIsAdmin,
                category = post.category,
                title = post.title,
                content = if (canView) post.content else JsonNodeFactory.instance.objectNode(),
                contentText = if (canView) post.contentText else "",
                imageUrls = if (canView) post.imageUrls.toList() else emptyList(),
                isSecret = post.isSecret,
                isPinned = post.isPinned,
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                liked = liked,
                isMine = isMine,
                viewerIsAdmin = viewerIsAdmin,
                canView = canView,
                createdAt = post.createdAt,
                updatedAt = post.updatedAt,
            )
    }
}

/**
 * 댓글 응답(camelCase). 삭제 댓글은 톰스톤(isDeleted=true, content 비움)으로 스레드 구조를 유지한다.
 * canView=false(비권한 비밀댓글) 또는 isDeleted면 content를 비운다.
 */
data class CommentResponse(
    val id: Long,
    val postId: Long,
    val parentId: Long?,
    val authorNickname: String,
    val authorIsAdmin: Boolean,
    val content: String,
    val isSecret: Boolean,
    val isMine: Boolean,
    val canView: Boolean,
    val isDeleted: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun of(
            comment: CommunityComment,
            authorNickname: String,
            authorIsAdmin: Boolean,
            isMine: Boolean,
            canView: Boolean,
        ): CommentResponse {
            val deleted = comment.deletedAt != null
            val visible = canView && !deleted
            return CommentResponse(
                id = requireNotNull(comment.id),
                postId = comment.postId,
                parentId = comment.parentId,
                authorNickname = authorNickname,
                authorIsAdmin = authorIsAdmin,
                content = if (visible) comment.content else "",
                isSecret = comment.isSecret,
                isMine = isMine,
                canView = canView,
                isDeleted = deleted,
                createdAt = comment.createdAt,
            )
        }
    }
}
