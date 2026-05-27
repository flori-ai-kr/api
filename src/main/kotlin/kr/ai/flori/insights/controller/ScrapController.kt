package kr.ai.flori.insights.controller

import jakarta.validation.Valid
import kr.ai.flori.insights.dto.*
import kr.ai.flori.insights.service.ScrapService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/insights/scraps")
class ScrapController(
    private val scrapService: ScrapService,
) {
    @PostMapping("/toggle")
    fun toggle(
        @Valid @RequestBody request: ScrapToggleRequest,
    ): ScrapToggleResponse = ScrapToggleResponse(scrapService.toggle(requireNotNull(request.targetType), requireNotNull(request.targetId)))

    @PutMapping("/memo")
    fun memo(
        @Valid @RequestBody request: ScrapMemoRequest,
    ): InsightScrapResponse = scrapService.updateMemo(requireNotNull(request.targetType), requireNotNull(request.targetId), request.memo)

    @GetMapping("/map")
    fun map(
        @RequestParam targetType: String,
    ): Map<Long, ScrapInfo> = scrapService.scrapMap(targetType)

    @GetMapping("/counts")
    fun counts(): ScrapCountsResponse = scrapService.counts()

    @GetMapping("/trends")
    fun trendScraps(
        @RequestParam(defaultValue = "100") limit: Int,
    ): List<TrendScrapResponse> = scrapService.trendScraps(limit)

    @GetMapping("/posts")
    fun postScraps(
        @RequestParam(defaultValue = "100") limit: Int,
    ): List<PostScrapResponse> = scrapService.postScraps(limit)
}
