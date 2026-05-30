package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminOverviewResponse
import kr.ai.flori.admin.dto.SalesCounts
import kr.ai.flori.admin.dto.SubscriptionCounts
import kr.ai.flori.admin.dto.UserCounts
import kr.ai.flori.admin.dto.VerificationCounts
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영 콘솔 cross-tenant 집계. 의도적으로 user_id 미필터(전 테넌트).
 * @RequiresAdmin 가드 하위에서만 호출된다. 매출 총액은 미수(unpaid) 제외(대시보드와 동일).
 */
@Service
class AdminStatsService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun overview(): AdminOverviewResponse =
        AdminOverviewResponse(
            users = userCounts(),
            sales = salesCounts(),
            subscriptions = subscriptionCounts(),
            verifications = verificationCounts(),
        )

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
              COALESCE(SUM(amount) FILTER (WHERE payment_method <> 'unpaid'), 0) AS total_amount,
              COUNT(*) FILTER (WHERE date >= CURRENT_DATE - INTERVAL '30 days'
                                 AND payment_method <> 'unpaid') AS last30d
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
}
