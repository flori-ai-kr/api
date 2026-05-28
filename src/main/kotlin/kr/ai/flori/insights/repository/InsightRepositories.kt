package kr.ai.flori.insights.repository

import kr.ai.flori.insights.entity.InsightScrap
import kr.ai.flori.insights.entity.InstagramAccount
import kr.ai.flori.insights.entity.InstagramPost
import kr.ai.flori.insights.entity.TrendArticle
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate

interface TrendArticleRepository : JpaRepository<TrendArticle, Long> {
    fun findByOrderByCollectedAtDescCreatedAtDesc(pageable: Pageable): List<TrendArticle>

    fun findByCategoryOrderByCollectedAtDescCreatedAtDesc(
        category: String,
        pageable: Pageable,
    ): List<TrendArticle>

    fun existsBySourceUrl(sourceUrl: String): Boolean

    fun countByCategory(category: String): Long

    /** since(수집일) 이후로 수집된 해당 카테고리 기사 개수. */
    fun countByCategoryAndCollectedAtGreaterThanEqual(
        category: String,
        collectedAt: LocalDate,
    ): Long
}

interface InstagramAccountRepository : JpaRepository<InstagramAccount, Long> {
    fun findByActiveTrueOrderBySortOrderAscUsernameAsc(): List<InstagramAccount>

    fun findAllByOrderBySortOrderAscUsernameAsc(): List<InstagramAccount>
}

interface InstagramPostRepository : JpaRepository<InstagramPost, Long> {
    @Query("SELECT p FROM InstagramPost p WHERE p.postedAt >= :since")
    fun findFeed(
        @Param("since") since: Instant,
        pageable: Pageable,
    ): List<InstagramPost>

    @Query("SELECT p FROM InstagramPost p WHERE p.accountId = :accountId AND p.postedAt >= :since")
    fun findFeedByAccount(
        @Param("accountId") accountId: Long,
        @Param("since") since: Instant,
        pageable: Pageable,
    ): List<InstagramPost>

    fun findByIdIn(ids: Collection<Long>): List<InstagramPost>

    fun existsByShortcode(shortcode: String): Boolean

    /** 가장 최근 수집(scraped_at) 시각. 포스트가 없으면 null. */
    @Query("SELECT MAX(p.scrapedAt) FROM InstagramPost p")
    fun findLatestScrapedAt(): Instant?
}

interface InsightScrapRepository : JpaRepository<InsightScrap, Long> {
    fun findByUserIdAndTargetTypeAndTargetId(
        userId: Long,
        targetType: String,
        targetId: Long,
    ): InsightScrap?

    fun findByUserIdAndTargetTypeOrderByCreatedAtDesc(
        userId: Long,
        targetType: String,
        pageable: Pageable,
    ): List<InsightScrap>

    fun findByUserIdAndTargetType(
        userId: Long,
        targetType: String,
    ): List<InsightScrap>

    fun countByUserIdAndTargetType(
        userId: Long,
        targetType: String,
    ): Long
}
