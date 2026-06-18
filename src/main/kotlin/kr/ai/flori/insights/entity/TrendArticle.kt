package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

/**
 * 트렌드·뉴스 기사(공유 큐레이션). 테넌트 간 공유 읽기 테이블 — user_id 격리 대상이 아니다.
 *
 * key_points: jsonb 문자열 배열(핵심 요점). collected_at: 수집일(DATE).
 * published_at: 원문 발행 시각(nullable). 적재(cron)는 후속 — 현재는 읽기 전용.
 * append-only 큐레이션이라 updated_at 없음 → BaseCreatedEntity.
 */
@Entity
@Table(name = "trend_articles")
class TrendArticle(
    @Column(name = "category", nullable = false)
    var category: String,
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "summary", nullable = false)
    var summary: String,
    @Column(name = "source_url", nullable = false)
    var sourceUrl: String,
    @Column(name = "collected_at", nullable = false)
    var collectedAt: LocalDate,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_points", columnDefinition = "jsonb", nullable = false)
    var keyPoints: List<String> = emptyList()

    @Column(name = "source_name")
    var sourceName: String? = null

    @Column(name = "published_at")
    var publishedAt: Instant? = null
}
