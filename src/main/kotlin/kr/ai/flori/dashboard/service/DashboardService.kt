package kr.ai.flori.dashboard.service

import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.KST
import kr.ai.flori.dashboard.dto.CategoryOption
import kr.ai.flori.dashboard.dto.CategoryStat
import kr.ai.flori.dashboard.dto.ChannelStat
import kr.ai.flori.dashboard.dto.CustomerStat
import kr.ai.flori.dashboard.dto.DashboardSummary
import kr.ai.flori.dashboard.dto.ExpenseCategoryStat
import kr.ai.flori.dashboard.dto.MonthDashboardResponse
import kr.ai.flori.dashboard.dto.PaymentMethodStat
import kr.ai.flori.dashboard.dto.TodayDashboardResponse
import kr.ai.flori.reservations.service.ReservationService
import kr.ai.flori.sales.service.SaleService
import kr.ai.flori.settings.service.SaleCategorySettingService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 대시보드/통계. 집계는 네이티브 SQL(JdbcTemplate), 모든 쿼리 user_id 바인딩(테넌트 격리·인젝션 방지).
 * 매출 합계·통계는 미수(unpaid)를 제외한다.
 */
@Service
class DashboardService(
    private val jdbcTemplate: JdbcTemplate,
    private val reservationService: ReservationService,
    private val saleService: SaleService,
    private val saleCategoryService: SaleCategorySettingService,
) {
    @Transactional(readOnly = true)
    fun today(): TodayDashboardResponse {
        val today = LocalDate.now(KST)
        return TodayDashboardResponse(
            summary = summary(TenantContext.currentUserId(), today, today),
            upcomingReservations = reservationService.upcoming(),
            triggeredReminders = reservationService.triggeredReminders(),
            recentSales = saleService.list(null, 0, RECENT_LIMIT, null, null, null, null).sales,
            saleCategories = saleCategoryService.list().map { CategoryOption(it.value, it.label) },
        )
    }

    @Transactional(readOnly = true)
    fun month(month: String?): MonthDashboardResponse {
        val userId = TenantContext.currentUserId()
        val ym = if (month.isNullOrBlank()) YearMonth.now(KST) else YearMonth.parse(month)
        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        return MonthDashboardResponse(
            summary = summary(userId, start, end),
            expenseTotal = expenseTotal(userId, start, end),
            categoryStats = categoryStats(userId, start, end),
            paymentStats = paymentStats(userId, start, end),
            channelStats = channelStats(userId, start, end),
            customerStats = customerStats(userId, start, end),
            expenseStats = expenseStats(userId, start, end),
        )
    }

    private fun summary(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): DashboardSummary =
        jdbcTemplate.queryForObject(
            """
            SELECT
              COALESCE(SUM(amount) FILTER (WHERE payment_method <> 'unpaid'), 0) AS total,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'card'), 0) AS card,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'cash'), 0) AS cash,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'transfer'), 0) AS transfer,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'naverpay'), 0) AS naverpay,
              COALESCE(SUM(amount) FILTER (WHERE payment_method = 'kakaopay'), 0) AS kakaopay,
              COUNT(*) FILTER (WHERE deposit_status = 'pending') AS pending_count,
              COALESCE(SUM(amount) FILTER (WHERE deposit_status = 'pending'), 0) AS pending_amount
            FROM sales WHERE user_id = ?::uuid AND date BETWEEN ? AND ?
            """.trimIndent(),
            { rs, _ ->
                DashboardSummary(
                    rs.getLong("total"),
                    rs.getLong("card"),
                    rs.getLong("cash"),
                    rs.getLong("transfer"),
                    rs.getLong("naverpay"),
                    rs.getLong("kakaopay"),
                    rs.getLong("pending_count"),
                    rs.getLong("pending_amount"),
                )
            },
            userId,
            Date.valueOf(start),
            Date.valueOf(end),
        ) ?: EMPTY_SUMMARY

    private fun expenseTotal(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): Long =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(total_amount), 0) FROM expenses WHERE user_id = ?::uuid AND date BETWEEN ? AND ?",
            Long::class.java,
            userId,
            Date.valueOf(start),
            Date.valueOf(end),
        ) ?: 0

    private fun categoryStats(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): List<CategoryStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT COALESCE(product_category, '기타') AS name, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::uuid AND date BETWEEN ? AND ? AND payment_method <> 'unpaid' " +
                    "GROUP BY COALESCE(product_category, '기타') ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getString("name"), rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val total = rows.sumOf { it.third }
        return rows.map { CategoryStat(it.first, it.second, it.third, percentage(it.third, total)) }
    }

    private fun paymentStats(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): List<PaymentMethodStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT payment_method AS m, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::uuid AND date BETWEEN ? AND ? AND payment_method <> 'unpaid' " +
                    "GROUP BY payment_method ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getString("m"), rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val total = rows.sumOf { it.third }
        return rows.map {
            PaymentMethodStat(it.first, PAYMENT_LABELS[it.first] ?: it.first, it.second, it.third, percentage(it.third, total))
        }
    }

    private fun channelStats(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): List<ChannelStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT COALESCE(reservation_channel, 'other') AS ch, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::uuid AND date BETWEEN ? AND ? AND payment_method <> 'unpaid' " +
                    "GROUP BY COALESCE(reservation_channel, 'other') ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getString("ch"), rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val total = rows.sumOf { it.third }
        return rows.map {
            ChannelStat(it.first, CHANNEL_LABELS[it.first] ?: it.first, it.second, it.third, percentage(it.third, total))
        }
    }

    private fun expenseStats(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): List<ExpenseCategoryStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT category, SUM(total_amount) AS amount FROM expenses " +
                    "WHERE user_id = ?::uuid AND date BETWEEN ? AND ? GROUP BY category ORDER BY amount DESC",
                { rs, _ -> rs.getString("category") to rs.getLong("amount") },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val total = rows.sumOf { it.second }
        return rows.map { ExpenseCategoryStat(it.first, EXPENSE_LABELS[it.first] ?: it.first, it.second, percentage(it.second, total)) }
    }

    private fun customerStats(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): CustomerStat {
        val total =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT customer_phone) FROM sales " +
                    "WHERE user_id = ?::uuid AND date BETWEEN ? AND ? AND customer_phone IS NOT NULL",
                Long::class.java,
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            ) ?: 0
        val returning =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT m.customer_phone) FROM sales m " +
                    "WHERE m.user_id = ?::uuid AND m.date BETWEEN ? AND ? AND m.customer_phone IS NOT NULL " +
                    "AND EXISTS (SELECT 1 FROM sales p WHERE p.user_id = m.user_id " +
                    "AND p.customer_phone = m.customer_phone AND p.date < ?)",
                Long::class.java,
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
                Date.valueOf(start),
            ) ?: 0
        return CustomerStat(total, returning, total - returning)
    }

    private fun percentage(
        amount: Long,
        total: Long,
    ): Int = if (total > 0) Math.round(amount.toDouble() / total * PERCENT).toInt() else 0

    private companion object {
        const val RECENT_LIMIT = 5
        const val PERCENT = 100
        val EMPTY_SUMMARY = DashboardSummary(0, 0, 0, 0, 0, 0, 0, 0)
        val PAYMENT_LABELS =
            mapOf("card" to "카드", "cash" to "현금", "transfer" to "계좌이체", "naverpay" to "네이버페이", "kakaopay" to "카카오페이")
        val CHANNEL_LABELS =
            mapOf("phone" to "전화", "kakaotalk" to "카카오톡", "naver_booking" to "네이버예약", "road" to "길거리", "other" to "기타")
        val EXPENSE_LABELS =
            mapOf(
                "flower_purchase" to "꽃 사입",
                "delivery" to "배송비",
                "advertising" to "광고비",
                "rent" to "임대료",
                "utilities" to "공과금",
                "supplies" to "소모품",
                "other" to "기타",
            )
    }
}
