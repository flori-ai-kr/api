package com.hazel.insights.dto

import com.hazel.insights.entity.InsightScrap
import com.hazel.insights.entity.InstagramAccount
import com.hazel.insights.entity.InstagramPost
import com.hazel.insights.entity.TrendArticle
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class TrendArticleResponse(
    val id: UUID,
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
    val id: UUID,
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
    val id: UUID,
    val accountId: UUID,
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
    val id: UUID,
    val targetType: String,
    val targetId: UUID,
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
    @field:NotNull val targetId: UUID?,
)

data class ScrapMemoRequest(
    @field:NotBlank val targetType: String?,
    @field:NotNull val targetId: UUID?,
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
    val id: UUID,
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
