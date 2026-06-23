package kr.ai.flori.community.entity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 커뮤니티 게시글. 단일 커뮤니티(테넌트 간 공유)이므로 user_id 격리 대상이 아니다 —
 * 신원/권한은 author_user_id 와 뷰어(JWT)로 계산한다.
 *
 * content: Tiptap JSON(jsonb). content_text: 검색/미리보기용 plain text.
 * like_count/comment_count: 비정규화(좋아요·댓글 작성/삭제 시 갱신).
 * 삭제는 soft delete(deleted_at).
 */
@Entity
@Table(name = "community_posts")
class CommunityPost(
    @Column(name = "author_user_id", nullable = false)
    var authorUserId: Long,
    @Column(name = "category", nullable = false)
    var category: String,
    @Column(name = "title", nullable = false)
    var title: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    var content: JsonNode = JsonNodeFactory.instance.objectNode()

    @Column(name = "content_text", nullable = false)
    var contentText: String = ""

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_urls", columnDefinition = "text[]")
    var imageUrls: Array<String> = emptyArray()

    @Column(name = "is_secret", nullable = false)
    var isSecret: Boolean = false

    @Column(name = "is_pinned", nullable = false)
    var isPinned: Boolean = false

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0

    @Column(name = "comment_count", nullable = false)
    var commentCount: Int = 0

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    @Column(name = "hidden_at")
    var hiddenAt: Instant? = null
        protected set

    @Column(name = "hidden_by")
    var hiddenBy: Long? = null
        protected set

    /** 운영자에 의한 숨김 처리(노출 제외). 게시글은 보존하되 일반 사용자에게 보이지 않는다. */
    fun hide(byUserId: Long) {
        hiddenAt = Instant.now()
        hiddenBy = byUserId
    }

    /** 숨김 해제(다시 노출). */
    fun unhide() {
        hiddenAt = null
        hiddenBy = null
    }
}
