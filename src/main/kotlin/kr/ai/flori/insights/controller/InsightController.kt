package kr.ai.flori.insights.controller

import jakarta.validation.Valid
import kr.ai.flori.insights.dto.AuctionPricesResponse
import kr.ai.flori.insights.dto.AuctionSummaryResponse
import kr.ai.flori.insights.dto.FlowerCategoryResponse
import kr.ai.flori.insights.dto.GrantScrapResponse
import kr.ai.flori.insights.dto.ScrapCountsResponse
import kr.ai.flori.insights.dto.ScrapInfoResponse
import kr.ai.flori.insights.dto.ScrapMemoRequest
import kr.ai.flori.insights.dto.ScrapResponse
import kr.ai.flori.insights.dto.ScrapToggleRequest
import kr.ai.flori.insights.dto.ScrapToggleResponse
import kr.ai.flori.insights.dto.SupportProgramResponse
import kr.ai.flori.insights.dto.TrendArticleResponse
import kr.ai.flori.insights.dto.TrendScrapResponse
import kr.ai.flori.insights.service.InsightService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 인사이트(정보 피드) API. 모든 엔드포인트 JWT 인증.
 * 트렌드/공판장/경매 시세/지원사업은 공유 읽기, 스크랩은 개인(user_id 격리, 서버가 토큰 기준 계산).
 */
@RestController
@RequestMapping("/insights")
class InsightController(
    private val insightService: InsightService,
) {
    // ── 트렌드·뉴스 ────────────────────────────────────────────────────────

    @GetMapping("/trends")
    fun listTrends(
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<TrendArticleResponse> = insightService.listTrends(category, offset, limit)

    @GetMapping("/trends/recent")
    fun recentTrends(
        @RequestParam(defaultValue = "3") perCategory: Int,
    ): Map<String, List<TrendArticleResponse>> = insightService.recentTrendsByCategory(perCategory)

    @GetMapping("/trends/counts")
    fun trendCounts(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) since: LocalDate?,
    ): Map<String, Long> = insightService.trendCountsByCategory(since)

    // ── 경매 시세 ──────────────────────────────────────────────────────────

    @GetMapping("/auction/categories")
    fun flowerCategories(): List<FlowerCategoryResponse> = insightService.listFlowerCategories()

    @GetMapping("/auction/dates")
    fun auctionDates(
        @RequestParam(required = false) gubn: String?,
    ): List<LocalDate> = insightService.auctionDates(gubn)

    @GetMapping("/auction/summary")
    fun auctionSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false) gubn: String?,
    ): AuctionSummaryResponse = insightService.auctionSummary(date, gubn)

    @GetMapping("/auction/prices")
    fun auctionPrices(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false) gubn: String?,
        @RequestParam(required = false) item: String?,
    ): AuctionPricesResponse = insightService.auctionPrices(date, gubn, item)

    // ── 지원사업 ───────────────────────────────────────────────────────────

    @GetMapping("/grants")
    fun listGrants(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<SupportProgramResponse> = insightService.listGrants(category, keyword, offset, limit)

    // ── 스크랩(개인) ───────────────────────────────────────────────────────

    @PostMapping("/scraps/toggle")
    fun toggleScrap(
        @Valid @RequestBody request: ScrapToggleRequest,
    ): ScrapToggleResponse = insightService.toggleScrap(request)

    @PutMapping("/scraps/memo")
    fun updateScrapMemo(
        @Valid @RequestBody request: ScrapMemoRequest,
    ): ScrapResponse = insightService.updateScrapMemo(request)

    @GetMapping("/scraps/map")
    fun scrapMap(
        @RequestParam targetType: String,
    ): Map<String, ScrapInfoResponse> = insightService.scrapMap(targetType)

    @GetMapping("/scraps/trends")
    fun trendScraps(
        @RequestParam(defaultValue = "100") limit: Int,
    ): List<TrendScrapResponse> = insightService.trendScraps(limit)

    @GetMapping("/scraps/grants")
    fun grantScraps(
        @RequestParam(defaultValue = "100") limit: Int,
    ): List<GrantScrapResponse> = insightService.grantScraps(limit)

    @GetMapping("/scraps/counts")
    fun scrapCounts(): ScrapCountsResponse = insightService.scrapCounts()
}
