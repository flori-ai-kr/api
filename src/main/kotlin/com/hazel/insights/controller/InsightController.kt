package com.hazel.insights.controller

import com.hazel.insights.dto.InstagramAccountResponse
import com.hazel.insights.dto.InstagramPostResponse
import com.hazel.insights.dto.TrendArticleResponse
import com.hazel.insights.service.InsightService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Insights", description = "인사이트 공유 읽기(트렌드/인스타)")
@RestController
@RequestMapping("/insights")
class InsightController(
    private val insightService: InsightService,
) {
    @Operation(summary = "트렌드 목록", description = "category/limit/offset")
    @GetMapping("/trends")
    fun trends(
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): List<TrendArticleResponse> = insightService.trends(category, limit, offset)

    @Operation(summary = "카테고리별 최신 트렌드")
    @GetMapping("/trends/recent")
    fun recentTrends(
        @RequestParam(defaultValue = "3") perCategory: Int,
    ): Map<String, List<TrendArticleResponse>> = insightService.recentTrendsByCategory(perCategory)

    @Operation(summary = "인스타 계정 목록")
    @GetMapping("/accounts")
    fun accounts(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
    ): List<InstagramAccountResponse> = insightService.accounts(activeOnly)

    @Operation(summary = "인스타 포스트 목록", description = "accountId/region/sortBy(latest|likes)/daysAgo/limit")
    @GetMapping("/posts")
    fun posts(
        @RequestParam(required = false) accountId: UUID?,
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) daysAgo: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<InstagramPostResponse> = insightService.posts(accountId, region, sortBy, daysAgo, limit)
}
