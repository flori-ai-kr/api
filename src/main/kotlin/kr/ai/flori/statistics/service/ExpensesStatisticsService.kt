package kr.ai.flori.statistics.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import kr.ai.flori.statistics.dto.DistributionItem
import kr.ai.flori.statistics.dto.ExpensesKpi
import kr.ai.flori.statistics.dto.ExpensesStatisticsResponse
import kr.ai.flori.statistics.dto.ExpensesTimePoint
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

/**
 * 지출 통계. 지출 합계·건수·카테고리 분포는 expenses(total_amount)로 산출하고,
 * 매출비·순이익은 미수 제외 매출(SalesStatisticsService.aggregate)을 기준으로 계산한다.
 */
@Service
class ExpensesStatisticsService(
    private val jdbcTemplate: JdbcTemplate,
    private val labelReader: LabelSettingReader,
    private val support: StatisticsSupport,
    private val sales: SalesStatisticsService,
) {
    @Transactional(readOnly = true)
    fun expensesStatistics(
        from: LocalDate,
        to: LocalDate,
    ): ExpensesStatisticsResponse {
        if (from.isAfter(to)) throw AppException(CommonErrorCode.VALIDATION, "from must not be after to")
        val userId = TenantContext.currentUserId()
        val prev = support.previousPeriod(from, to)

        val curExpense = expenseTotal(userId, from, to)
        val prevExpense = expenseTotal(userId, prev.from, prev.to)
        val curSales = sales.aggregate(userId, from, to).total
        val prevSales = sales.aggregate(userId, prev.from, prev.to).total

        val netProfit = curSales - curExpense.total
        val prevNetProfit = prevSales - prevExpense.total

        val kpi =
            ExpensesKpi(
                totalAmount = curExpense.total,
                totalAmountDeltaPct = support.pct(curExpense.total, prevExpense.total),
                count = curExpense.count,
                countDelta = curExpense.count - prevExpense.count,
                expenseRatioPct = support.percentage(curExpense.total, curSales),
                netProfit = netProfit,
                netProfitDeltaPct = support.pct(netProfit, prevNetProfit),
            )

        return ExpensesStatisticsResponse(
            kpi = kpi,
            timeseries = expenseTimeseries(userId, from, to),
            categoryDistribution = expenseCategoryDistribution(userId, from, to, curExpense.total),
        )
    }

    /** 지출 합계·건수(기간 내 전체, 미수 개념 없음). */
    private fun expenseTotal(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): ExpenseAggregate =
        jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(total_amount), 0) AS total, COUNT(*) AS cnt
            FROM expenses WHERE user_id = ?::bigint AND date BETWEEN ? AND ?
            """.trimIndent(),
            { rs, _ -> ExpenseAggregate(rs.getLong("total"), rs.getLong("cnt")) },
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
        ) ?: EMPTY_EXPENSE_AGGREGATE

    /** 일별 지출·순이익 시계열. 지출 또는 매출 활동이 있는 날만 포함하고 일자 오름차순 정렬한다. */
    private fun expenseTimeseries(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<ExpensesTimePoint> {
        val expenseByDay =
            jdbcTemplate
                .query(
                    """
                    SELECT date AS d, COALESCE(SUM(total_amount), 0) AS amt FROM expenses
                    WHERE user_id = ?::bigint AND date BETWEEN ? AND ? GROUP BY date ORDER BY date
                    """.trimIndent(),
                    { rs, _ -> rs.getDate("d").toLocalDate() to rs.getLong("amt") },
                    userId,
                    Date.valueOf(from),
                    Date.valueOf(to),
                ).toMap()
        val salesByDay = sales.timeseries(userId, from, to).associate { it.date to it.amount }

        return (expenseByDay.keys + salesByDay.keys).distinct().sorted().map { date ->
            val expense = expenseByDay[date] ?: 0
            val salesAmount = salesByDay[date] ?: 0
            ExpensesTimePoint(date, expense, salesAmount - expense)
        }
    }

    /** category_id 그룹 지출 분포. id가 null이면 라벨은 "기타". */
    private fun expenseCategoryDistribution(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
        totalAmount: Long,
    ): List<DistributionItem> {
        val rows =
            jdbcTemplate.query(
                "SELECT category_id AS gid, COUNT(*) AS cnt, SUM(total_amount) AS amount " +
                    "FROM expenses WHERE user_id = ?::bigint AND date BETWEEN ? AND ? " +
                    "GROUP BY category_id ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getLong("gid").takeUnless { rs.wasNull() }, rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(from),
                Date.valueOf(to),
            )
        val labels = labelReader.labelMap(LabelDomains.EXPENSE, LabelKinds.CATEGORY)
        return rows.map {
            DistributionItem(
                it.first,
                it.first?.let { id -> labels[id] } ?: StatisticsSupport.ETC,
                it.third,
                it.second,
                support.percentage(it.third, totalAmount),
            )
        }
    }

    private data class ExpenseAggregate(
        val total: Long,
        val count: Long,
    )

    private companion object {
        val EMPTY_EXPENSE_AGGREGATE = ExpenseAggregate(0, 0)
    }
}
