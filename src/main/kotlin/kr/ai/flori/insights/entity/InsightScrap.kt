package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.util.UUID

/**
 * 인사이트 스크랩(트렌드/포스트 공용, polymorphic: target_type + target_id).
 * 멀티테넌시: user_id 격리. (user_id, target_type, target_id) 복합 unique.
 */
@Entity
@Table(name = "insight_scraps")
class InsightScrap(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "target_type", nullable = false)
    var targetType: String,
    @Column(name = "target_id", nullable = false)
    var targetId: UUID,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "memo")
    var memo: String? = null
}
