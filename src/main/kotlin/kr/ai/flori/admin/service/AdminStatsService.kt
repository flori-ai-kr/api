package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminOverviewResponse
import kr.ai.flori.admin.dto.OverviewComparison
import kr.ai.flori.admin.dto.SalesCounts
import kr.ai.flori.admin.dto.SubscriptionCounts
import kr.ai.flori.admin.dto.TimeseriesPoint
import kr.ai.flori.admin.dto.UserCounts
import kr.ai.flori.admin.dto.VerificationCounts
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

/**
 * 운영 콘솔 cross-tenant 집계. 의도적으로 user_id 미필터(전 테넌트).
 * @RequiresAdmin 가드 하위에서만 호출된다. 매출 총액/건수는 미수(unpaid) 제외(대시보드와 동일).
 */
@Service
class AdminStatsService(
    private val jdbc: JdbcTemplate,
) {
    /** range: 7d|30d|90d|all. 기본 카운트는 전체, comparison은 선택 기간 대비 직전 동기간 증감. */
    @Transactional(readOnly = true)
    fun overview(range: String): AdminOverviewResponse =
        AdminOverviewResponse(
            users = userCounts(),
            sales = salesCounts(),
            subscriptions = subscriptionCounts(),
            verifications = verificationCounts(),
            comparison = comparison(range),
        )

    /** metric: signups|sales. 선택 기간 일별 카운트(빈 날짜는 0). */
    @Transactional(readOnly = true)
    fun timeseries(
        metric: String,
        range: String,
    ): List<TimeseriesPoint> {
        val from = LocalDate.now().minusDays((daysForRange(range) - 1).toLong())
        val sql =
            when (metric) {
                "signups" ->
                    """
                    SELECT d::date AS day, COUNT(u.id) AS cnt
                    FROM generate_series(?::date, CURRENT_DATE, '1 day'::interval) d
                    LEFT JOIN users u ON u.created_at::date = d::date
                    GROUP BY d ORDER BY d
                    """.trimIndent()
                "sales" ->
                    """
                    SELECT d::date AS day, COUNT(s.id) AS cnt
                    FROM generate_series(?::date, CURRENT_DATE, '1 day'::interval) d
                    LEFT JOIN sales s ON s.date = d::date AND s.payment_method_id IS NOT NULL
                    GROUP BY d ORDER BY d
                    """.trimIndent()
                else -> throw AppException(CommonErrorCode.VALIDATION)
            }
        return jdbc.query(sql, { rs, _ -> TimeseriesPoint(rs.getDate("day").toLocalDate(), rs.getLong("cnt")) }, Date.valueOf(from))
    }

    private fun comparison(range: String): OverviewComparison? {
        if (range == RANGE_ALL) return null
        val days = daysForRange(range).toLong()
        val today = LocalDate.now()
        val curFrom = today.minusDays(days - 1)
        val prevFrom = today.minusDays(days * 2 - 1)
        val prevTo = today.minusDays(days)
        return OverviewComparison(
            usersChangePct = changePct(signupCount(curFrom, today), signupCount(prevFrom, prevTo)),
            salesCountChangePct = changePct(salesCount(curFrom, today), salesCount(prevFrom, prevTo)),
        )
    }

    private fun signupCount(
        from: LocalDate,
        to: LocalDate,
    ): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE created_at::date BETWEEN ? AND ?",
            Long::class.java,
            Date.valueOf(from),
            Date.valueOf(to),
        ) ?: 0

    private fun salesCount(
        from: LocalDate,
        to: LocalDate,
    ): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sales WHERE date BETWEEN ? AND ? AND payment_method_id IS NOT NULL",
            Long::class.java,
            Date.valueOf(from),
            Date.valueOf(to),
        ) ?: 0

    private fun changePct(
        current: Long,
        previous: Long,
    ): Double? = if (previous == 0L) null else (current - previous) * PERCENT / previous

    private fun userCounts(): UserCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE is_active) AS active,
              COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM user_profiles p WHERE p.user_id = u.id)) AS onboarded
            FROM users u
            """.trimIndent(),
        ) { rs, _ -> UserCounts(rs.getLong("total"), rs.getLong("active"), rs.getLong("onboarded")) }!!

    private fun salesCounts(): SalesCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) AS entry_count,
              COALESCE(SUM(amount) FILTER (WHERE payment_method_id IS NOT NULL), 0) AS total_amount,
              COUNT(*) FILTER (WHERE date >= CURRENT_DATE - INTERVAL '30 days'
                                 AND payment_method_id IS NOT NULL) AS last30d
            FROM sales
            """.trimIndent(),
        ) { rs, _ -> SalesCounts(rs.getLong("entry_count"), rs.getLong("total_amount"), rs.getLong("last30d")) }!!

    private fun subscriptionCounts(): SubscriptionCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) FILTER (WHERE status = 'active') AS active,
              COUNT(*) FILTER (WHERE status = 'in_grace') AS in_grace,
              COUNT(*) FILTER (WHERE status = 'expired') AS expired,
              COUNT(*) FILTER (WHERE status = 'none') AS none
            FROM subscriptions
            """.trimIndent(),
        ) { rs, _ ->
            SubscriptionCounts(rs.getLong("active"), rs.getLong("in_grace"), rs.getLong("expired"), rs.getLong("none"))
        }!!

    private fun verificationCounts(): VerificationCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
              COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
              COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected
            FROM business_verifications
            """.trimIndent(),
        ) { rs, _ -> VerificationCounts(rs.getLong("pending"), rs.getLong("approved"), rs.getLong("rejected")) }!!

    // 단일 range→일수 매핑. 잘못된 range는 400(silent fallback 금지). all=최근 365일 캡.
    private fun daysForRange(range: String): Int =
        when (range) {
            RANGE_7D -> DAYS_7
            RANGE_30D -> DAYS_30
            RANGE_90D -> DAYS_90
            RANGE_ALL -> DAYS_ALL
            else -> throw AppException(CommonErrorCode.VALIDATION)
        }

    private companion object {
        const val RANGE_7D = "7d"
        const val RANGE_30D = "30d"
        const val RANGE_90D = "90d"
        const val RANGE_ALL = "all"
        const val DAYS_7 = 7
        const val DAYS_30 = 30
        const val DAYS_90 = 90
        const val DAYS_ALL = 365
        const val PERCENT = 100.0
    }
}
