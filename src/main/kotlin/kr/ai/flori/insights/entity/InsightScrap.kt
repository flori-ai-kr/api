package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 인사이트 스크랩(사장님별 개인 북마크). 개인(user_id) 테이블 — 모든 쿼리는 user_id 로 격리한다.
 *
 * target_type: 'trend'(trend_articles) | 'grant'(support_programs). target_id 는 FK 없는 간접참조.
 * (user_id, target_type, target_id) UNIQUE — 같은 대상은 한 번만 스크랩(멱등).
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

    /** 메모 갱신(도메인 메서드 — 클래스 레벨 @Setter 미사용). 빈 문자열은 null 로 정규화. */
    fun updateMemo(value: String?) {
        memo = value?.takeIf { it.isNotBlank() }
    }
}
