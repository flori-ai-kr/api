package kr.ai.flori.community.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import java.time.Instant

/**
 * 커뮤니티 댓글. parent_id 셀프참조로 대댓글(1단계) 표현.
 * 부모 댓글이 비밀이면 자식도 비밀 강제(서비스). 삭제는 soft delete(deleted_at) — 스레드 구조 유지를 위해 톰스톤으로 노출.
 */
@Entity
@Table(name = "community_comments")
class CommunityComment(
    @Column(name = "post_id", nullable = false)
    var postId: Long,
    @Column(name = "author_user_id", nullable = false)
    var authorUserId: Long,
    @Column(name = "content", nullable = false)
    var content: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "parent_id")
    var parentId: Long? = null

    @Column(name = "is_secret", nullable = false)
    var isSecret: Boolean = false

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
}
