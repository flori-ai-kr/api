package kr.ai.flori.insights.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.ai.flori.insights.dto.InsightScrapResponse
import kr.ai.flori.insights.dto.PostScrapResponse
import kr.ai.flori.insights.dto.ScrapCountsResponse
import kr.ai.flori.insights.dto.ScrapInfo
import kr.ai.flori.insights.dto.ScrapMemoRequest
import kr.ai.flori.insights.dto.ScrapToggleRequest
import kr.ai.flori.insights.dto.ScrapToggleResponse
import kr.ai.flori.insights.dto.TrendScrapResponse
import kr.ai.flori.insights.service.ScrapService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Scraps", description = "인사이트 스크랩")
@RestController
@RequestMapping("/insights/scraps")
class ScrapController(
    private val scrapService: ScrapService,
) {
    @Operation(summary = "스크랩 토글")
    @PostMapping("/toggle")
    fun toggle(
        @Valid @RequestBody request: ScrapToggleRequest,
    ): ScrapToggleResponse = ScrapToggleResponse(scrapService.toggle(requireNotNull(request.targetType), requireNotNull(request.targetId)))

    @Operation(summary = "스크랩 메모 수정", description = "스크랩 후에만 가능")
    @PutMapping("/memo")
    fun memo(
        @Valid @RequestBody request: ScrapMemoRequest,
    ): InsightScrapResponse = scrapService.updateMemo(requireNotNull(request.targetType), requireNotNull(request.targetId), request.memo)

    @Operation(summary = "스크랩 맵", description = "targetType별 target_id → {id, memo}")
    @GetMapping("/map")
    fun map(
        @RequestParam targetType: String,
    ): Map<UUID, ScrapInfo> = scrapService.scrapMap(targetType)

    @Operation(summary = "스크랩 개수")
    @GetMapping("/counts")
    fun counts(): ScrapCountsResponse = scrapService.counts()

    @Operation(summary = "트렌드 스크랩 목록")
    @GetMapping("/trends")
    fun trendScraps(
        @RequestParam(defaultValue = "100") limit: Int,
    ): List<TrendScrapResponse> = scrapService.trendScraps(limit)

    @Operation(summary = "포스트 스크랩 목록")
    @GetMapping("/posts")
    fun postScraps(
        @RequestParam(defaultValue = "100") limit: Int,
    ): List<PostScrapResponse> = scrapService.postScraps(limit)
}
