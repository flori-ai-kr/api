package kr.ai.flori.insights.service

import kr.ai.flori.insights.dto.InstagramAccountResponse
import kr.ai.flori.insights.dto.InstagramPostResponse
import kr.ai.flori.insights.dto.TrendArticleResponse
import kr.ai.flori.insights.repository.InstagramAccountRepository
import kr.ai.flori.insights.repository.InstagramPostRepository
import kr.ai.flori.insights.repository.TrendArticleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 인사이트 공유 읽기(트렌드/계정/포스트). 테넌트 무관(인증만 필요) — user_id 격리 예외.
 */
@Service
class InsightService(
    private val trendRepository: TrendArticleRepository,
    private val accountRepository: InstagramAccountRepository,
    private val postRepository: InstagramPostRepository,
) {
    @Transactional(readOnly = true)
    fun trends(
        category: String?,
        limit: Int,
        offset: Int,
    ): List<TrendArticleResponse> {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val pageable = PageRequest.of(offset.coerceAtLeast(0) / safeLimit, safeLimit)
        val rows =
            if (category.isNullOrBlank()) {
                trendRepository.findByOrderByCollectedAtDescCreatedAtDesc(pageable)
            } else {
                trendRepository.findByCategoryOrderByCollectedAtDescCreatedAtDesc(category, pageable)
            }
        return rows.map(TrendArticleResponse::from)
    }

    @Transactional(readOnly = true)
    fun recentTrendsByCategory(perCategory: Int): Map<String, List<TrendArticleResponse>> {
        val pageable = PageRequest.of(0, perCategory.coerceIn(1, MAX_LIMIT))
        return TREND_CATEGORIES.associateWith { category ->
            trendRepository
                .findByCategoryOrderByCollectedAtDescCreatedAtDesc(category, pageable)
                .map(TrendArticleResponse::from)
        }
    }

    /**
     * 카테고리별 트렌드 개수. since(수집일) 이후만 카운트(옵션). 모든 카테고리 키를 포함(0 포함).
     */
    @Transactional(readOnly = true)
    fun trendCounts(since: LocalDate?): Map<String, Long> =
        TREND_CATEGORIES.associateWith { category ->
            if (since == null) {
                trendRepository.countByCategory(category)
            } else {
                trendRepository.countByCategoryAndCollectedAtGreaterThanEqual(category, since)
            }
        }

    /** 가장 최근 instagram_posts 수집 시각(없으면 null). "마지막 업데이트" 표시용. */
    @Transactional(readOnly = true)
    fun latestInstagramCollectedAt(): Instant? = postRepository.findLatestScrapedAt()

    @Transactional(readOnly = true)
    fun accounts(activeOnly: Boolean): List<InstagramAccountResponse> =
        (
            if (activeOnly) {
                accountRepository.findByActiveTrueOrderBySortOrderAscUsernameAsc()
            } else {
                accountRepository.findAllByOrderBySortOrderAscUsernameAsc()
            }
        ).map(InstagramAccountResponse::from)

    @Transactional(readOnly = true)
    fun posts(
        accountId: Long?,
        region: String?,
        sortBy: String?,
        daysAgo: Int?,
        limit: Int,
    ): List<InstagramPostResponse> {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        // null 파라미터 IS NULL 회피: 필터 없으면 EPOCH(항상 참인 하한)
        val since = daysAgo?.let { Instant.now().minus(it.toLong(), ChronoUnit.DAYS) } ?: Instant.EPOCH
        val sort =
            if (sortBy == "likes") {
                Sort.by(Sort.Order.desc("likeCount"))
            } else {
                Sort.by(Sort.Order.desc("postedAt"))
            }
        // region 필터는 fetch 후 적용하므로 버퍼를 더 가져온다.
        val fetchLimit = if (region != null) safeLimit * REGION_BUFFER else safeLimit
        val pageable = PageRequest.of(0, fetchLimit, sort)
        val fetched =
            if (accountId != null) {
                postRepository.findFeedByAccount(accountId, since, pageable)
            } else {
                postRepository.findFeed(since, pageable)
            }
        // FK 연관관계 대신 accountId로 계정을 별도 조회해 합친다(간접참조).
        val accountsById = accountRepository.findAllById(fetched.map { it.accountId }.toSet()).associateBy { it.id }
        val filtered =
            if (region != null) {
                fetched.filter { accountsById[it.accountId]?.region == region }
            } else {
                fetched
            }
        return filtered.take(safeLimit).map { InstagramPostResponse.from(it, accountsById[it.accountId]) }
    }

    private companion object {
        const val MAX_LIMIT = 100
        const val REGION_BUFFER = 3
        val TREND_CATEGORIES = listOf("flower", "inspiration", "business", "industry")
    }
}
