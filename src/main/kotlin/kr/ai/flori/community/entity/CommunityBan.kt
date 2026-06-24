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
 * 커뮤니티 활동 차단(밴). 차단된 사용자는 글/댓글 작성이 금지된다.
 * active ban = lifted_at IS NULL AND (expires_at IS NULL OR expires_at > now).
 * expires_at=null 이면 영구 차단, lift()로 수동 해제한다.
 */
@Entity
@Table(name = "community_bans")
class CommunityBan(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "banned_by", nullable = false)
    var bannedBy: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "reason")
    var reason: String? = null

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "lifted_at")
    var liftedAt: Instant? = null
        protected set

    /** 차단 해제. */
    fun lift() {
        liftedAt = Instant.now()
    }

    /** 현재 시점 기준 활성 차단 여부(해제되지 않고 만료되지 않음). */
    fun isActive(now: Instant = Instant.now()): Boolean = liftedAt == null && (expiresAt == null || expiresAt!!.isAfter(now))
}
