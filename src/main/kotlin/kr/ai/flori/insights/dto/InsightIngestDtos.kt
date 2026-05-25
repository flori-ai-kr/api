package kr.ai.flori.insights.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/** 내부 수집 입력. */
data class TrendArticleIngest(
    @field:NotBlank val category: String?,
    @field:NotBlank val title: String?,
    @field:NotBlank val summary: String?,
    val keyPoints: List<String> = emptyList(),
    @field:NotBlank val sourceUrl: String?,
    val sourceName: String? = null,
    val publishedAt: Instant? = null,
)

data class TrendArticlesBulkRequest(
    @field:NotEmpty
    @field:Valid
    val articles: List<TrendArticleIngest>?,
)

data class InstagramPostIngest(
    @field:NotNull val accountId: UUID?,
    @field:NotBlank val shortcode: String?,
    @field:NotBlank val permalink: String?,
    val imageUrls: List<String> = emptyList(),
    val caption: String? = null,
    val likeCount: Int = 0,
    @field:NotNull val postedAt: Instant?,
)

data class InstagramPostsBulkRequest(
    @field:NotEmpty
    @field:Valid
    val posts: List<InstagramPostIngest>?,
)

data class InstagramAccountCreateRequest(
    @field:NotBlank val username: String?,
    val displayName: String? = null,
    @field:NotBlank val region: String?,
    val sortOrder: Int = 0,
    val active: Boolean = true,
    val notes: String? = null,
)

data class InstagramAccountUpdateRequest(
    val username: String? = null,
    val displayName: String? = null,
    val region: String? = null,
    val sortOrder: Int? = null,
    val active: Boolean? = null,
    val notes: String? = null,
)

data class IngestResultResponse(
    val inserted: Int,
    val skipped: Int,
)
