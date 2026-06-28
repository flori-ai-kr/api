package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/**
 * 경매 품목 스크랩(사장님별 관심 품목 북마크). 개인(user_id) 테이블 — 모든 쿼리는 user_id 로 격리한다.
 *
 * 경매 품목은 id가 아니라 이름(pum_name)이라 insight_scraps(target_id BIGINT)와 별도 테이블로 둔다.
 * (user_id, pum_name) UNIQUE — 같은 품목은 한 번만 스크랩(멱등). append-only 라 updated_at 없음.
 */
@Entity
@Table(name = "flower_item_scraps")
class FlowerItemScrap(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "pum_name", nullable = false)
    var pumName: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}
