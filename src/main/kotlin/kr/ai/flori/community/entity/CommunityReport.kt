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
 * 커뮤니티 신고. target_type('post'|'comment') + target_id 로 게시글/댓글을 가리킨다.
 * (target_type, target_id, reporter_user_id) UNIQUE — 동일인이 같은 대상을 중복 신고하지 못한다.
 * 운영자가 처리하면 status='resolved'|'dismissed' 로 전이하고 resolution(처리 방식)을 기록한다.
 */
@Entity
@Table(name = "community_reports")
class CommunityReport(
    @Column(name = "target_type", nullable = false)
    var targetType: String,
    @Column(name = "target_id", nullable = false)
    var targetId: Long,
    @Column(name = "reporter_user_id", nullable = false)
    var reporterUserId: Long,
    @Column(name = "reason", nullable = false)
    var reason: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "detail")
    var detail: String? = null

    @Column(name = "status", nullable = false)
    var status: String = STATUS_PENDING
        protected set

    @Column(name = "resolved_by")
    var resolvedBy: Long? = null
        protected set

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
        protected set

    @Column(name = "resolution")
    var resolution: String? = null
        protected set

    /**
     * 신고 처리. resolution='dismissed'면 기각(status='dismissed'), 그 외(deleted/hidden)는 처리완료(status='resolved').
     */
    fun resolve(
        byUserId: Long,
        resolution: String,
    ) {
        status = if (resolution == RESOLUTION_DISMISSED) STATUS_DISMISSED else STATUS_RESOLVED
        resolvedBy = byUserId
        resolvedAt = Instant.now()
        this.resolution = resolution
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_RESOLVED = "resolved"
        const val STATUS_DISMISSED = "dismissed"
        const val RESOLUTION_DISMISSED = "dismissed"
        const val TARGET_POST = "post"
        const val TARGET_COMMENT = "comment"
    }
}
