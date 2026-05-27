package kr.ai.flori.insights.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kr.ai.flori.insights.entity.InsightScrap
import kr.ai.flori.insights.entity.InstagramAccount
import kr.ai.flori.insights.entity.InstagramPost
import kr.ai.flori.insights.entity.TrendArticle
import java.time.Instant
import java.time.LocalDate

data class TrendArticleResponse(
    val id: Long,
    val category: String,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val sourceUrl: String,
    val sourceName: String?,
    val publishedAt: Instant?,
    val collectedAt: LocalDate,
    val createdAt: Instant,
) {
    companion object {
        fun from(a: TrendArticle) =
            TrendArticleResponse(
                requireNotNull(a.id),
                a.category,
                a.title,
                a.summary,
                a.keyPoints,
                a.sourceUrl,
                a.sourceName,
                a.publishedAt,
                a.collectedAt,
                a.createdAt,
            )
    }
}

data class InstagramAccountResponse(
    val id: Long,
    val username: String,
    val displayName: String?,
    val profileUrl: String,
    val region: String,
    val sortOrder: Int,
    val active: Boolean,
    val notes: String?,
) {
    companion object {
        fun from(a: InstagramAccount) =
            InstagramAccountResponse(
                requireNotNull(a.id),
                a.username,
                a.displayName,
                a.profileUrl,
                a.region,
                a.sortOrder,
                a.active,
                a.notes,
            )
    }
}

data class InstagramPostResponse(
    val id: Long,
    val accountId: Long,
    val shortcode: String,
    val permalink: String,
    val imageUrls: List<String>,
    val caption: String?,
    val likeCount: Int,
    val postedAt: Instant,
    val account: InstagramAccountResponse?,
) {
    companion object {
        fun from(p: InstagramPost) =
            InstagramPostResponse(
                requireNotNull(p.id),
                p.accountId,
                p.shortcode,
                p.permalink,
                p.imageUrls,
                p.caption,
                p.likeCount,
                p.postedAt,
                p.account?.let(InstagramAccountResponse::from),
            )
    }
}

data class InsightScrapResponse(
    val id: Long,
    val targetType: String,
    val targetId: Long,
    val memo: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(s: InsightScrap) =
            InsightScrapResponse(
                requireNotNull(s.id),
                s.targetType,
                s.targetId,
                s.memo,
                s.createdAt,
                s.updatedAt,
            )
    }
}

data class ScrapToggleRequest(
    @field:NotBlank val targetType: String?,
    @field:NotNull val targetId: Long?,
)

data class ScrapMemoRequest(
    @field:NotBlank val targetType: String?,
    @field:NotNull val targetId: Long?,
    val memo: String? = null,
)

data class ScrapToggleResponse(
    val scraped: Boolean,
)

data class ScrapCountsResponse(
    val trend: Long,
    val post: Long,
)

data class ScrapInfo(
    val id: Long,
    val memo: String?,
)

data class TrendScrapResponse(
    val scrap: InsightScrapResponse,
    val article: TrendArticleResponse,
)

data class PostScrapResponse(
    val scrap: InsightScrapResponse,
    val post: InstagramPostResponse,
)
