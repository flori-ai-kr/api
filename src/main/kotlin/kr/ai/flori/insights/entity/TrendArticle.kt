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
 * 트렌드 기사. 공유 데이터(테넌트 무관) — 인증 사용자면 누구나 조회. 쓰기는 내부 API만.
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
    @Column(name = "key_points", columnDefinition = "jsonb")
    var keyPoints: List<String> = emptyList()

    @Column(name = "source_name")
    var sourceName: String? = null

    @Column(name = "published_at")
    var publishedAt: Instant? = null
}
