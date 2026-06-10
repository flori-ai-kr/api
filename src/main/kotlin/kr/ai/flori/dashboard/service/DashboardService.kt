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
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import kr.ai.flori.settings.service.SaleCategorySettingService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

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
    private val labelReader: LabelSettingReader,
) {
    @Transactional(readOnly = true)
    fun today(): TodayDashboardResponse {
        val today = LocalDate.now(KST)
        return TodayDashboardResponse(
            summary = summary(TenantContext.currentUserId(), today, today),
            upcomingReservations = reservationService.upcoming(),
            triggeredReminders = reservationService.triggeredReminders(),
            recentSales = saleService.list(null, null, null, 0, RECENT_LIMIT, null, null, null, null).sales,
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
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): DashboardSummary =
        jdbcTemplate.queryForObject(
            """
            SELECT
              COALESCE(SUM(s.amount) FILTER (WHERE s.payment_method_id IS NOT NULL), 0) AS total,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'card'), 0) AS card,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'cash'), 0) AS cash,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'transfer'), 0) AS transfer,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'naverpay'), 0) AS naverpay,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'kakaopay'), 0) AS kakaopay
            FROM sales s LEFT JOIN label_settings ls ON ls.id = s.payment_method_id AND ls.user_id = s.user_id
            WHERE s.user_id = ?::bigint AND s.date BETWEEN ? AND ?
            """.trimIndent(),
            { rs, _ ->
                DashboardSummary(
                    rs.getLong("total"),
                    rs.getLong("card"),
                    rs.getLong("cash"),
                    rs.getLong("transfer"),
                    rs.getLong("naverpay"),
                    rs.getLong("kakaopay"),
                )
            },
            userId,
            Date.valueOf(start),
            Date.valueOf(end),
        ) ?: EMPTY_SUMMARY

    private fun expenseTotal(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): Long =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(total_amount), 0) FROM expenses WHERE user_id = ?::bigint AND date BETWEEN ? AND ?",
            Long::class.java,
            userId,
            Date.valueOf(start),
            Date.valueOf(end),
        ) ?: 0

    private fun categoryStats(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<CategoryStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT category_id AS cid, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND payment_method_id IS NOT NULL " +
                    "GROUP BY category_id ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getLong("cid").takeUnless { rs.wasNull() }, rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val labels = labelReader.labelMap(LabelDomains.SALE, LabelKinds.CATEGORY)
        val total = rows.sumOf { it.third }
        return rows.map {
            CategoryStat(it.first, it.first?.let { id -> labels[id] } ?: ETC, it.second, it.third, percentage(it.third, total))
        }
    }

    private fun paymentStats(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<PaymentMethodStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT payment_method_id AS pid, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND payment_method_id IS NOT NULL " +
                    "GROUP BY payment_method_id ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getLong("pid").takeUnless { rs.wasNull() }, rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val labels = labelReader.labelMap(LabelDomains.SALE, LabelKinds.PAYMENT)
        val total = rows.sumOf { it.third }
        return rows.map {
            PaymentMethodStat(it.first, it.first?.let { id -> labels[id] } ?: ETC, it.second, it.third, percentage(it.third, total))
        }
    }

    private fun channelStats(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<ChannelStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT channel_id AS cid, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND payment_method_id IS NOT NULL " +
                    "GROUP BY channel_id ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getLong("cid").takeUnless { rs.wasNull() }, rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val labels = labelReader.labelMap(LabelDomains.SALE, LabelKinds.CHANNEL)
        val total = rows.sumOf { it.third }
        return rows.map {
            ChannelStat(it.first, it.first?.let { id -> labels[id] } ?: ETC, it.second, it.third, percentage(it.third, total))
        }
    }

    private fun expenseStats(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<ExpenseCategoryStat> {
        val rows =
            jdbcTemplate.query(
                "SELECT category_id AS cid, SUM(total_amount) AS amount FROM expenses " +
                    "WHERE user_id = ?::bigint AND date BETWEEN ? AND ? GROUP BY category_id ORDER BY amount DESC",
                { rs, _ -> rs.getLong("cid").takeUnless { rs.wasNull() } to rs.getLong("amount") },
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            )
        val labels = labelReader.labelMap(LabelDomains.EXPENSE, LabelKinds.CATEGORY)
        val total = rows.sumOf { it.second }
        return rows.map {
            ExpenseCategoryStat(it.first, it.first?.let { id -> labels[id] } ?: ETC, it.second, percentage(it.second, total))
        }
    }

    private fun customerStats(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): CustomerStat {
        val total =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT customer_phone) FROM sales " +
                    "WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND customer_phone IS NOT NULL",
                Long::class.java,
                userId,
                Date.valueOf(start),
                Date.valueOf(end),
            ) ?: 0
        val returning =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT m.customer_phone) FROM sales m " +
                    "WHERE m.user_id = ?::bigint AND m.date BETWEEN ? AND ? AND m.customer_phone IS NOT NULL " +
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
    ): Int = if (total > 0) (amount.toDouble() / total * PERCENT).roundToInt() else 0

    private companion object {
        const val RECENT_LIMIT = 5
        const val PERCENT = 100

        /** id가 null인(설정에서 삭제됐거나 미지정) 집계 버킷의 표시 라벨. */
        const val ETC = "기타"
        val EMPTY_SUMMARY = DashboardSummary(0, 0, 0, 0, 0, 0)
    }
}
