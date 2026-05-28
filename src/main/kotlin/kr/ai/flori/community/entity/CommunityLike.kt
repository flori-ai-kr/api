package kr.ai.flori.community.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/**
 * 커뮤니티 좋아요. (post_id, user_id) 유일 — 사용자당 글 1회.
 */
@Entity
@Table(name = "community_likes")
class CommunityLike(
    @Column(name = "post_id", nullable = false)
    var postId: Long,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}
