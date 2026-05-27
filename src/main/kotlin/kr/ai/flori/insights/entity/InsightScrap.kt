package kr.ai.flori.insights.entity

import jakarta.persistence.*
import kr.ai.flori.common.entity.BaseEntity

/**
 * 인사이트 스크랩(트렌드/포스트 공용, polymorphic: target_type + target_id).
 * 멀티테넌시: user_id 격리. (user_id, target_type, target_id) 복합 unique.
 */
@Entity
@Table(name = "insight_scraps")
class InsightScrap(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "target_type", nullable = false)
    var targetType: String,
    @Column(name = "target_id", nullable = false)
    var targetId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "memo")
    var memo: String? = null
}
