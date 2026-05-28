package kr.ai.flori.insights.controller

import kr.ai.flori.insights.dto.InstagramAccountResponse
import kr.ai.flori.insights.dto.InstagramLatestResponse
import kr.ai.flori.insights.dto.InstagramPostResponse
import kr.ai.flori.insights.dto.TrendArticleResponse
import kr.ai.flori.insights.service.InsightService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/insights")
class InsightController(
    private val insightService: InsightService,
) {
    @GetMapping("/trends")
    fun trends(
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): List<TrendArticleResponse> = insightService.trends(category, limit, offset)

    @GetMapping("/trends/recent")
    fun recentTrends(
        @RequestParam(defaultValue = "3") perCategory: Int,
    ): Map<String, List<TrendArticleResponse>> = insightService.recentTrendsByCategory(perCategory)

    @GetMapping("/trends/counts")
    fun trendCounts(
        @RequestParam(required = false) since: LocalDate?,
    ): Map<String, Long> = insightService.trendCounts(since)

    @GetMapping("/instagram/latest")
    fun instagramLatest(): InstagramLatestResponse = InstagramLatestResponse(insightService.latestInstagramCollectedAt())

    @GetMapping("/accounts")
    fun accounts(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
    ): List<InstagramAccountResponse> = insightService.accounts(activeOnly)

    @GetMapping("/posts")
    fun posts(
        @RequestParam(required = false) accountId: Long?,
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) daysAgo: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<InstagramPostResponse> = insightService.posts(accountId, region, sortBy, daysAgo, limit)
}
