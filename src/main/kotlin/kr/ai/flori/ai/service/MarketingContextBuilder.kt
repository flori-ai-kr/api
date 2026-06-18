package kr.ai.flori.ai.service

import kr.ai.flori.ai.client.AiStoreContext
import kr.ai.flori.statistics.service.SalesStatisticsService
import kr.ai.flori.user.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * store_context 조립기(pluggable). 매장 실데이터를 LLM 1인칭 경험 신호로 코드 조립한다(LLM 도구콜 아님).
 *
 * 모든 소스는 best-effort — 데이터/기능이 없으면 해당 필드를 graceful degrade로 생략한다.
 * (블로그 생성은 키워드만으로도 동작해야 하므로 맥락 조립 실패가 생성을 막지 않는다.)
 *
 * - shop_name: user_profiles.store_name (PK=user_id → 본질적 테넌트 격리)
 * - avg_order_value / top_products: 최근 [LOOKBACK_DAYS]일 매출 통계(SalesStatisticsService, TenantContext 내부 격리)
 * - upcoming_season: [SeasonCalendar] 결정적 D-day
 */
@Component
class MarketingContextBuilder(
    private val userProfileRepository: UserProfileRepository,
    private val salesStatisticsService: SalesStatisticsService,
    private val seasonCalendar: SeasonCalendar,
) {
    /** 현재 테넌트([userId])의 store_context를 조립한다. 모든 필드가 비면 null(맥락 없음). */
    fun build(userId: Long): AiStoreContext? {
        val shopName = shopName(userId)
        val sales = salesContext()
        val upcoming = upcomingSeason()

        val context =
            AiStoreContext(
                shopName = shopName,
                avgOrderValue = sales?.first,
                upcomingSeason = upcoming,
                topProducts = sales?.second ?: emptyList(),
            )
        val empty =
            context.shopName == null &&
                context.avgOrderValue == null &&
                context.upcomingSeason == null &&
                context.topProducts.isEmpty()
        return if (empty) null else context
    }

    private fun shopName(userId: Long): String? =
        runBestEffort("shop_name") {
            userProfileRepository
                .findById(userId)
                .orElse(null)
                ?.storeName
                ?.takeIf { it.isNotBlank() }
        }

    /** (avg_order_value, top_products) — 매출 0건이면 둘 다 비운다. */
    private fun salesContext(): Pair<Long?, List<String>>? =
        runBestEffort("sales") {
            val to = LocalDate.now(SEOUL)
            val from = to.minusDays(LOOKBACK_DAYS)
            val stats = salesStatisticsService.salesStatistics(from, to)
            val avg = stats.kpi.avgOrderValue.takeIf { it > 0 }
            val products =
                stats.categoryDistribution
                    .asSequence()
                    .filter { it.count > 0 }
                    .map { it.label }
                    .filter { it.isNotBlank() && it != ETC_LABEL }
                    .take(TOP_PRODUCTS)
                    .toList()
            if (avg == null && products.isEmpty()) null else avg to products
        }

    private fun upcomingSeason(): String? = runBestEffort("upcoming_season") { seasonCalendar.upcoming(LocalDate.now(SEOUL)) }

    /** 단일 맥락 소스 실패가 생성을 막지 않도록 삼키고 null 반환(디버그 로깅만). */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> runBestEffort(
        source: String,
        block: () -> T?,
    ): T? =
        try {
            block()
        } catch (e: Exception) {
            log.debug("store_context source '{}' unavailable, omitting", source, e)
            null
        }

    private companion object {
        val log = LoggerFactory.getLogger(MarketingContextBuilder::class.java)
        val SEOUL: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")
        const val LOOKBACK_DAYS = 90L
        const val TOP_PRODUCTS = 3
        const val ETC_LABEL = "기타" // StatisticsSupport.ETC — 미분류 카테고리는 신호가 약해 제외
    }
}
