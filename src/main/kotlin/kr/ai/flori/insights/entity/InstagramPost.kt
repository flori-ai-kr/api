package kr.ai.flori.insights.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 인스타그램 포스트. 공유 데이터. 쓰기는 내부 API만. account는 읽기 전용 연관.
 */
@Entity
@Table(name = "instagram_posts")
class InstagramPost(
    @Column(name = "account_id", nullable = false)
    var accountId: Long,
    @Column(name = "shortcode", nullable = false)
    var shortcode: String,
    @Column(name = "permalink", nullable = false)
    var permalink: String,
    @Column(name = "posted_at", nullable = false)
    var postedAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", columnDefinition = "jsonb")
    var imageUrls: List<String> = emptyList()

    @Column(name = "caption")
    var caption: String? = null

    @Column(name = "like_count")
    var likeCount: Int = 0

    @Column(name = "scraped_at")
    var scrapedAt: Instant = Instant.now()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    var account: InstagramAccount? = null
}
