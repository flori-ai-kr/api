package kr.ai.flori.insights.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.insights.domain.FlowerCategories
import kr.ai.flori.insights.entity.InsightScrap
import kr.ai.flori.insights.entity.SupportProgram
import kr.ai.flori.insights.entity.TrendArticle
import kr.ai.flori.insights.repository.AuctionPriceRow
import kr.ai.flori.insights.repository.AuctionSummaryRow
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ── 트렌드·뉴스 ─────────────────────────────────────────────────────────────

/** 트렌드 기사 응답(camelCase). web KotlinTrendArticle 미러. */
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
        fun from(a: TrendArticle): TrendArticleResponse =
            TrendArticleResponse(
                id = requireNotNull(a.id),
                category = a.category,
                title = a.title,
                summary = a.summary,
                keyPoints = a.keyPoints,
                sourceUrl = a.sourceUrl,
                sourceName = a.sourceName,
                publishedAt = a.publishedAt,
                collectedAt = a.collectedAt,
                createdAt = a.createdAt,
            )
    }
}

// ── 경매 시세 ───────────────────────────────────────────────────────────────

/** 화훼 경매 카테고리(aT f001 flowerGubn). code=요청 파라미터, label=응답 한글 텍스트. */
data class FlowerCategoryResponse(
    val code: String,
    val label: String,
) {
    companion object {
        fun from(c: FlowerCategories.Category): FlowerCategoryResponse = FlowerCategoryResponse(c.code, c.label)
    }
}

/**
 * 경매 시세 한 행(camelCase). 단일 시장(aT 양재) — 시장/법인 필드 없음.
 * prevAvgAmt/changeRate 는 직전 정산일자 대비 서버 파생값.
 * changeRate 는 비율(예: 0.05 = +5%). 직전 평균가가 없으면 prevAvgAmt/changeRate 둘 다 null.
 */
data class AuctionPriceResponse(
    val flowerGubn: String,
    val pumName: String,
    val goodName: String,
    val lvNm: String,
    val avgAmt: Int?,
    val maxAmt: Int?,
    val minAmt: Int?,
    val totQty: Long?,
    val totAmt: Long?,
    val prevAvgAmt: Int?,
    val changeRate: Double?,
) {
    companion object {
        fun from(row: AuctionPriceRow): AuctionPriceResponse =
            AuctionPriceResponse(
                flowerGubn = row.flowerGubn,
                pumName = row.pumName,
                goodName = row.goodName,
                lvNm = row.lvNm,
                avgAmt = row.avgAmt,
                maxAmt = row.maxAmt,
                minAmt = row.minAmt,
                totQty = row.totQty,
                totAmt = row.totAmt,
                prevAvgAmt = row.prevAvgAmt,
                changeRate = row.changeRate,
            )
    }
}

/**
 * 경매 시세 조회 응답: 기준 정산일자 + 출처 + 시세 행 목록.
 * date 가 null 이면 데이터가 없는 것. source 는 이용허락범위(제작자 표시) 준수용 출처 표기.
 */
data class AuctionPricesResponse(
    val date: LocalDate?,
    val source: String,
    val prices: List<AuctionPriceResponse>,
)

/**
 * 경매 요약 한 품목(pum_name 단위, camelCase).
 * - repAvg = 거래량 가중평균(round(sum(tot_amt)/sum(tot_qty)), 거래량 0/없으면 null).
 * - repChangeRate = 등락 방식 A(매칭 품종·등급 등락률 중앙값, 비율. 매칭 변형 없으면 null).
 * - variantCount = 그 품목·그날의 (good_name, lv_nm) 행 수.
 */
data class AuctionSummaryItem(
    val pumName: String,
    val repAvg: Int?,
    val repChangeRate: Double?,
    val variantCount: Long,
) {
    companion object {
        fun from(row: AuctionSummaryRow): AuctionSummaryItem =
            AuctionSummaryItem(
                pumName = row.pumName,
                repAvg = row.repAvg,
                repChangeRate = row.repChangeRate,
                variantCount = row.variantCount,
            )
    }
}

/**
 * 경매 요약 조회 응답: 기준 정산일자(최신일) + 출처 + 품목 요약 목록(거래량 많은 순).
 * date 가 null 이면 데이터가 없는 것. source 는 이용허락범위(제작자 표시) 준수용 출처 표기.
 */
data class AuctionSummaryResponse(
    val date: LocalDate?,
    val source: String,
    val items: List<AuctionSummaryItem>,
)

// ── 지원사업 ───────────────────────────────────────────────────────────────

/** 지원사업 응답(camelCase). dDay 는 apply_end 까지 남은 일수(서버 파생, 마감일/없으면 null). */
data class SupportProgramResponse(
    val id: Long,
    val source: String,
    val title: String,
    val agency: String?,
    val category: String?,
    val target: String?,
    val summary: String?,
    val applyStart: LocalDate?,
    val applyEnd: LocalDate?,
    val sourceUrl: String?,
    // Jackson 기본 네이밍은 dDay → "dday" 로 직렬화되므로 명시적으로 camelCase 키를 고정한다(웹 계약).
    @get:JsonProperty("dDay")
    val dDay: Long?,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            p: SupportProgram,
            today: LocalDate,
        ): SupportProgramResponse =
            SupportProgramResponse(
                id = requireNotNull(p.id),
                source = p.source,
                title = p.title,
                agency = p.agency,
                category = p.category,
                target = p.target,
                summary = p.summary,
                applyStart = p.applyStart,
                applyEnd = p.applyEnd,
                sourceUrl = p.sourceUrl,
                dDay = p.applyEnd?.let { ChronoUnit.DAYS.between(today, it) },
                createdAt = p.createdAt,
            )
    }
}

// ── 스크랩(개인) ────────────────────────────────────────────────────────────

/** 스크랩 토글 요청. */
data class ScrapToggleRequest(
    @field:NotBlank(message = "대상 유형(targetType)은 필수입니다")
    val targetType: String?,
    @field:NotNull(message = "대상 ID(targetId)는 필수입니다")
    val targetId: Long?,
)

/** 스크랩 메모 수정 요청. memo 가 null/공백이면 메모를 비운다. */
data class ScrapMemoRequest(
    @field:NotBlank(message = "대상 유형(targetType)은 필수입니다")
    val targetType: String?,
    @field:NotNull(message = "대상 ID(targetId)는 필수입니다")
    val targetId: Long?,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
)

/** 스크랩 토글 결과. */
data class ScrapToggleResponse(
    val scraped: Boolean,
)

/** 경매 품목 스크랩 토글 요청(품목명 단위). */
data class FlowerItemScrapToggleRequest(
    @field:NotBlank(message = "품목명(pumName)은 필수입니다")
    @field:Size(max = FieldLimits.NAME, message = "품목명이 너무 깁니다")
    val pumName: String?,
)

/** 경매 품목 스크랩 목록(내가 스크랩한 품목명). */
data class FlowerItemScrapListResponse(
    val pumNames: List<String>,
)

/** 스크랩 단건(camelCase). web KotlinScrap 미러. */
data class ScrapResponse(
    val id: Long,
    val targetType: String,
    val targetId: Long,
    val memo: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(s: InsightScrap): ScrapResponse =
            ScrapResponse(
                id = requireNotNull(s.id),
                targetType = s.targetType,
                targetId = s.targetId,
                memo = s.memo,
                createdAt = s.createdAt,
                updatedAt = s.updatedAt,
            )
    }
}

/** 스크랩 맵 값(목록 enrichment 없이 id/memo 만). 키는 targetId(문자열). */
data class ScrapInfoResponse(
    val id: Long,
    val memo: String?,
)

/** 트렌드 스크랩(스크랩 + 대상 기사 조인). web KotlinTrendScrap 미러. */
data class TrendScrapResponse(
    val scrap: ScrapResponse,
    val article: TrendArticleResponse,
)

/** 지원사업 스크랩(스크랩 + 대상 사업 조인). */
data class GrantScrapResponse(
    val scrap: ScrapResponse,
    val program: SupportProgramResponse,
)

/** 스크랩 카운트(대상 유형별). */
data class ScrapCountsResponse(
    val trend: Long,
    val grant: Long,
)
