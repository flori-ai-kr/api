package kr.ai.flori.insights.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.KST
import kr.ai.flori.common.util.Paging
import kr.ai.flori.insights.domain.FlowerCategories
import kr.ai.flori.insights.domain.ScrapTargetTypes
import kr.ai.flori.insights.domain.TrendCategories
import kr.ai.flori.insights.dto.AuctionPriceResponse
import kr.ai.flori.insights.dto.AuctionPricesResponse
import kr.ai.flori.insights.dto.AuctionSummaryItem
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
import kr.ai.flori.insights.entity.InsightScrap
import kr.ai.flori.insights.error.InsightErrorCode
import kr.ai.flori.insights.repository.FlowerAuctionPriceQueryRepository
import kr.ai.flori.insights.repository.InsightScrapRepository
import kr.ai.flori.insights.repository.SupportProgramRepository
import kr.ai.flori.insights.repository.TrendArticleRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 인사이트(정보 피드) 서비스.
 *
 * - 트렌드/공판장/경매 시세/지원사업은 공유 읽기(테넌트 무관). 등락률·dDay 는 서버 파생.
 * - 스크랩은 개인(user_id) — 모든 쿼리를 TenantContext.currentUserId() 로 격리한다(멀티테넌시 HARD).
 */
@Service
class InsightService(
    private val trendArticleRepository: TrendArticleRepository,
    private val auctionPriceQueryRepository: FlowerAuctionPriceQueryRepository,
    private val supportProgramRepository: SupportProgramRepository,
    private val scrapRepository: InsightScrapRepository,
) {
    // ── 트렌드·뉴스 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listTrends(
        category: String?,
        offset: Int,
        limit: Int,
    ): List<TrendArticleResponse> {
        val pageable = Paging.offsetLimit(offset, limit, MAX_LIMIT)
        return trendArticleRepository
            .findFeed(category?.takeIf { it.isNotBlank() }, pageable)
            .content
            .map(TrendArticleResponse::from)
    }

    /** 대시보드용: 카테고리별 최신 N개씩. 키는 카테고리 4종 고정. */
    @Transactional(readOnly = true)
    fun recentTrendsByCategory(perCategory: Int): Map<String, List<TrendArticleResponse>> {
        val safePer = perCategory.coerceIn(1, MAX_PER_CATEGORY)
        val pageable = Paging.offsetLimit(0, safePer, MAX_PER_CATEGORY)
        return TrendCategories.ALL.associateWith { category ->
            trendArticleRepository.findFeed(category, pageable).content.map(TrendArticleResponse::from)
        }
    }

    /** 카테고리별 기사 수. since(수집일 이후) 옵션. 키는 카테고리 4종 고정. */
    @Transactional(readOnly = true)
    fun trendCountsByCategory(since: LocalDate?): Map<String, Long> {
        // 카테고리당 1쿼리지만 카테고리는 4개로 고정이라 비용이 작다(목록 페이지 진입 시 1회).
        val all = trendArticleRepository.findFeed(null, Paging.offsetLimit(0, COUNT_SCAN_LIMIT, COUNT_SCAN_LIMIT)).content
        return TrendCategories.ALL.associateWith { category ->
            all.count { it.category == category && (since == null || !it.collectedAt.isBefore(since)) }.toLong()
        }
    }

    // ── 경매 시세 ──────────────────────────────────────────────────────────

    /** 화훼 경매 카테고리(절화/관엽/난/춘란) 정적 목록. 단일 시장(aT 양재). */
    fun listFlowerCategories(): List<FlowerCategoryResponse> = FlowerCategories.ALL.map(FlowerCategoryResponse::from)

    /**
     * 데이터가 있는 정산일자 최신순 목록(date picker용). gubn(절화/관엽/난/춘란 텍스트) 옵션 필터.
     * 상한(DATE_LIST_CAP, 약 60일)을 둔다.
     */
    @Transactional(readOnly = true)
    fun auctionDates(gubn: String?): List<LocalDate> =
        auctionPriceQueryRepository.distinctDates(gubn?.takeIf { it.isNotBlank() }, DATE_LIST_CAP)

    /**
     * 경매 요약: 대상일(없으면 완전한 최신 정산일)의 품목(pum_name) 단위 요약 + 출처.
     * gubn 은 flower_gubn(절화/관엽/난/춘란 텍스트) 필터. repChangeRate 는 등락 방식 A(매칭 품종·등급 중앙값).
     * 데이터가 전혀 없으면 date=null, items=[] 로 응답한다(404 대신 빈 결과 — 표시 전용 UX).
     */
    @Transactional(readOnly = true)
    fun auctionSummary(
        date: LocalDate?,
        gubn: String?,
    ): AuctionSummaryResponse {
        val gubnFilter = gubn?.takeIf { it.isNotBlank() }
        val targetDate =
            date ?: auctionPriceQueryRepository.latestCompleteDate(gubnFilter)
                ?: return AuctionSummaryResponse(date = null, source = FlowerCategories.SOURCE, items = emptyList())
        val items = auctionPriceQueryRepository.summaryOn(targetDate, gubnFilter).map(AuctionSummaryItem::from)
        return AuctionSummaryResponse(date = targetDate, source = FlowerCategories.SOURCE, items = items)
    }

    /**
     * 경매 시세 드릴다운: 대상일(없으면 완전한 최신 정산일)의 시세 행 + 직전 정산일자 대비 등락률(파생).
     * gubn 은 flower_gubn(절화/관엽/난/춘란 텍스트), item 은 pum_name(품목) 필터.
     * date 기본값은 요약과 동일하게 "완전한 최신 정산일"을 쓴다(요약↔드릴다운 일관성).
     * 데이터가 전혀 없으면 date=null, prices=[] 로 응답한다(404 대신 빈 결과 — 표시 전용 UX).
     * source 는 이용허락범위(제작자 표시) 준수용 출처 표기로 항상 채운다.
     */
    @Transactional(readOnly = true)
    fun auctionPrices(
        date: LocalDate?,
        gubn: String?,
        item: String?,
    ): AuctionPricesResponse {
        val gubnFilter = gubn?.takeIf { it.isNotBlank() }
        val itemFilter = item?.takeIf { it.isNotBlank() }
        val targetDate =
            date ?: auctionPriceQueryRepository.latestCompleteDate(gubnFilter)
                ?: return AuctionPricesResponse(date = null, source = FlowerCategories.SOURCE, prices = emptyList())
        val rows = auctionPriceQueryRepository.ratesOn(targetDate, gubnFilter, itemFilter).map(AuctionPriceResponse::from)
        return AuctionPricesResponse(date = targetDate, source = FlowerCategories.SOURCE, prices = rows)
    }

    // ── 지원사업 ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listGrants(
        category: String?,
        offset: Int,
        limit: Int,
    ): List<SupportProgramResponse> {
        val today = LocalDate.now(KST)
        val pageable = Paging.offsetLimit(offset, limit, MAX_LIMIT)
        return supportProgramRepository
            .findFeed(category?.takeIf { it.isNotBlank() }, pageable)
            .content
            .map { SupportProgramResponse.from(it, today) }
    }

    // ── 스크랩(개인) ───────────────────────────────────────────────────────

    /** 스크랩 토글(멱등). 없으면 생성(true), 있으면 삭제(false). 대상 존재를 먼저 검증한다. */
    @Transactional
    fun toggleScrap(request: ScrapToggleRequest): ScrapToggleResponse {
        val userId = TenantContext.currentUserId()
        val targetType = requireTargetType(requireNotNull(request.targetType))
        val targetId = requireNotNull(request.targetId)
        requireTargetExists(targetType, targetId)

        val existing = scrapRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
        if (existing != null) {
            scrapRepository.delete(existing)
            return ScrapToggleResponse(scraped = false)
        }
        try {
            scrapRepository.save(InsightScrap(userId = userId, targetType = targetType, targetId = targetId))
        } catch (_: DataIntegrityViolationException) {
            // 동시 토글 경쟁: UNIQUE(user_id,target_type,target_id) 위반 → 이미 스크랩된 것으로 간주(멱등).
        }
        return ScrapToggleResponse(scraped = true)
    }

    /** 스크랩 메모 수정. 아직 스크랩 안 했으면 메모와 함께 생성(upsert). */
    @Transactional
    fun updateScrapMemo(request: ScrapMemoRequest): ScrapResponse {
        val userId = TenantContext.currentUserId()
        val targetType = requireTargetType(requireNotNull(request.targetType))
        val targetId = requireNotNull(request.targetId)
        requireTargetExists(targetType, targetId)

        val scrap =
            scrapRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
                ?: InsightScrap(userId = userId, targetType = targetType, targetId = targetId)
        scrap.updateMemo(request.memo)
        return ScrapResponse.from(scrapRepository.save(scrap))
    }

    /** 특정 대상 유형의 스크랩 맵(targetId → {id, memo}). 목록 화면의 스크랩 여부/메모 표시용. */
    @Transactional(readOnly = true)
    fun scrapMap(targetType: String): Map<String, ScrapInfoResponse> {
        val userId = TenantContext.currentUserId()
        val type = requireTargetType(targetType)
        return scrapRepository
            .findByUserIdAndTargetTypeOrderByCreatedAtDesc(userId, type)
            .associate { it.targetId.toString() to ScrapInfoResponse(requireNotNull(it.id), it.memo) }
    }

    /** 트렌드 스크랩 목록(스크랩 + 대상 기사 조인, 최신순). 삭제된 대상은 제외. */
    @Transactional(readOnly = true)
    fun trendScraps(limit: Int): List<TrendScrapResponse> {
        val userId = TenantContext.currentUserId()
        val scraps =
            scrapRepository
                .findByUserIdAndTargetTypeOrderByCreatedAtDesc(userId, ScrapTargetTypes.TREND)
                .take(limit.coerceIn(1, MAX_LIMIT))
        if (scraps.isEmpty()) return emptyList()
        val articles = trendArticleRepository.findByIdIn(scraps.map { it.targetId }).associateBy { requireNotNull(it.id) }
        return scraps.mapNotNull { scrap ->
            val article = articles[scrap.targetId] ?: return@mapNotNull null
            TrendScrapResponse(ScrapResponse.from(scrap), TrendArticleResponse.from(article))
        }
    }

    /** 지원사업 스크랩 목록(스크랩 + 대상 사업 조인, 최신순). 삭제된 대상은 제외. */
    @Transactional(readOnly = true)
    fun grantScraps(limit: Int): List<GrantScrapResponse> {
        val userId = TenantContext.currentUserId()
        val today = LocalDate.now(KST)
        val scraps =
            scrapRepository
                .findByUserIdAndTargetTypeOrderByCreatedAtDesc(userId, ScrapTargetTypes.GRANT)
                .take(limit.coerceIn(1, MAX_LIMIT))
        if (scraps.isEmpty()) return emptyList()
        val programs = supportProgramRepository.findByIdIn(scraps.map { it.targetId }).associateBy { requireNotNull(it.id) }
        return scraps.mapNotNull { scrap ->
            val program = programs[scrap.targetId] ?: return@mapNotNull null
            GrantScrapResponse(ScrapResponse.from(scrap), SupportProgramResponse.from(program, today))
        }
    }

    /** 대상 유형별 스크랩 수. */
    @Transactional(readOnly = true)
    fun scrapCounts(): ScrapCountsResponse {
        val userId = TenantContext.currentUserId()
        return ScrapCountsResponse(
            trend = scrapRepository.countByUserIdAndTargetType(userId, ScrapTargetTypes.TREND),
            grant = scrapRepository.countByUserIdAndTargetType(userId, ScrapTargetTypes.GRANT),
        )
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private fun requireTargetType(value: String): String {
        if (value !in ScrapTargetTypes.ALL) throw AppException(InsightErrorCode.INVALID_TARGET_TYPE)
        return value
    }

    /** 스크랩 대상이 실재하는지 검증(IDOR/유령 스크랩 방지). 공유 데이터라 id 만으로 조회. */
    private fun requireTargetExists(
        targetType: String,
        targetId: Long,
    ) {
        val exists =
            when (targetType) {
                ScrapTargetTypes.TREND -> trendArticleRepository.existsById(targetId)
                ScrapTargetTypes.GRANT -> supportProgramRepository.existsById(targetId)
                else -> false
            }
        if (!exists) throw AppException(InsightErrorCode.SCRAP_TARGET_NOT_FOUND)
    }

    private companion object {
        const val MAX_LIMIT = 100
        const val MAX_PER_CATEGORY = 20
        const val COUNT_SCAN_LIMIT = 1000

        /** 날짜 선택기 목록 상한(약 2개월). */
        const val DATE_LIST_CAP = 60
    }
}
