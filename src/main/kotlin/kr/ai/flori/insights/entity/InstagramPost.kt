package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 인스타그램 포스트. 공유 데이터. 쓰기는 내부 API만.
 * account는 JPA 연관관계를 두지 않고 accountId(간접참조)만 보유한다 —
 * 계정 정보가 필요하면 서비스가 accountId로 별도 조회해 합친다.
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
}
