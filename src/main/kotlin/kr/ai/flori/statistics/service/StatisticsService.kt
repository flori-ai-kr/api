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
import kr.ai.flori.statistics.dto.SalesKpi
import kr.ai.flori.statistics.dto.SalesStatisticsResponse
import kr.ai.flori.statistics.dto.SalesTimePoint
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 매출 통계. 집계는 네이티브 SQL(JdbcTemplate), 모든 쿼리 user_id 바인딩(테넌트 격리·인젝션 방지).
 * 매출 합계·분포·시계열은 미수(unpaid, payment_method_id IS NULL)를 제외하며, 미수는 별도 KPI로 산출한다.
 * 증감(delta)은 직전 동일 길이 기간과 비교한다.
 */
@Service
class StatisticsService(
    private val jdbcTemplate: JdbcTemplate,
    private val labelReader: LabelSettingReader,
) {
    @Transactional(readOnly = true)
    fun salesStatistics(
        from: LocalDate,
        to: LocalDate,
    ): SalesStatisticsResponse {
        if (from.isAfter(to)) throw AppException(CommonErrorCode.VALIDATION, "from must not be after to")
        val userId = TenantContext.currentUserId()
        val days = ChronoUnit.DAYS.between(from, to) + 1
        val pFrom = from.minusDays(days)
        val pTo = from.minusDays(1)

        val cur = aggregate(userId, from, to)
        val prev = aggregate(userId, pFrom, pTo)

        val kpi =
            SalesKpi(
                totalAmount = cur.total,
                totalAmountDeltaPct = pct(cur.total, prev.total),
                count = cur.count,
                countDelta = cur.count - prev.count,
                avgOrderValue = cur.avg(),
                avgOrderValueDeltaPct = pct(cur.avg(), prev.avg()),
                unpaidBalance = cur.unpaidAmount,
                unpaidCount = cur.unpaidCount,
            )

        return SalesStatisticsResponse(
            kpi = kpi,
            timeseries = timeseries(userId, from, to),
            categoryDistribution = distribution(userId, from, to, GroupColumn.CATEGORY, LabelKinds.CATEGORY, cur.total),
            paymentDistribution = distribution(userId, from, to, GroupColumn.PAYMENT, LabelKinds.PAYMENT, cur.total),
            channelDistribution = distribution(userId, from, to, GroupColumn.CHANNEL, LabelKinds.CHANNEL, cur.total),
        )
    }

    /**
     * 지출 통계. 지출 합계·건수·카테고리 분포는 expenses(total_amount)로 산출하고,
     * 매출비·순이익은 미수 제외 매출(aggregate)을 기준으로 계산한다.
     */
    @Transactional(readOnly = true)
    fun expensesStatistics(
        from: LocalDate,
        to: LocalDate,
    ): ExpensesStatisticsResponse {
        if (from.isAfter(to)) throw AppException(CommonErrorCode.VALIDATION, "from must not be after to")
        val userId = TenantContext.currentUserId()
        val days = ChronoUnit.DAYS.between(from, to) + 1
        val pFrom = from.minusDays(days)
        val pTo = from.minusDays(1)

        val curExpense = expenseTotal(userId, from, to)
        val prevExpense = expenseTotal(userId, pFrom, pTo)
        val curSales = aggregate(userId, from, to).total
        val prevSales = aggregate(userId, pFrom, pTo).total

        val netProfit = curSales - curExpense.total
        val prevNetProfit = prevSales - prevExpense.total

        val kpi =
            ExpensesKpi(
                totalAmount = curExpense.total,
                totalAmountDeltaPct = pct(curExpense.total, prevExpense.total),
                count = curExpense.count,
                countDelta = curExpense.count - prevExpense.count,
                expenseRatioPct = percentage(curExpense.total, curSales),
                netProfit = netProfit,
                netProfitDeltaPct = pct(netProfit, prevNetProfit),
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
        val salesByDay = timeseries(userId, from, to).associate { it.date to it.amount }

        return (expenseByDay.keys + salesByDay.keys).distinct().sorted().map { date ->
            val expense = expenseByDay[date] ?: 0
            val sales = salesByDay[date] ?: 0
            ExpensesTimePoint(date, expense, sales - expense)
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
            DistributionItem(it.first, it.first?.let { id -> labels[id] } ?: ETC, it.third, it.second, percentage(it.third, totalAmount))
        }
    }

    private fun aggregate(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): Aggregate =
        jdbcTemplate.queryForObject(
            """
            SELECT
              COALESCE(SUM(amount) FILTER (WHERE payment_method_id IS NOT NULL), 0) AS total,
              COUNT(*)            FILTER (WHERE payment_method_id IS NOT NULL)       AS cnt,
              COALESCE(SUM(amount) FILTER (WHERE is_unpaid AND payment_method_id IS NULL), 0) AS unpaid_amt,
              COUNT(*)            FILTER (WHERE is_unpaid AND payment_method_id IS NULL)       AS unpaid_cnt
            FROM sales WHERE user_id = ?::bigint AND date BETWEEN ? AND ?
            """.trimIndent(),
            { rs, _ ->
                Aggregate(
                    total = rs.getLong("total"),
                    count = rs.getLong("cnt"),
                    unpaidAmount = rs.getLong("unpaid_amt"),
                    unpaidCount = rs.getLong("unpaid_cnt"),
                )
            },
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
        ) ?: EMPTY_AGGREGATE

    private fun timeseries(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<SalesTimePoint> =
        jdbcTemplate.query(
            """
            SELECT date AS d, COALESCE(SUM(amount), 0) AS amt, COUNT(*) AS cnt
            FROM sales WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND payment_method_id IS NOT NULL
            GROUP BY date ORDER BY date
            """.trimIndent(),
            { rs, _ -> SalesTimePoint(rs.getDate("d").toLocalDate(), rs.getLong("amt"), rs.getLong("cnt")) },
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
        )

    /** category_id/payment_method_id/channel_id 그룹 분포(미수 제외). id가 null이면 라벨은 "기타". */
    private fun distribution(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
        groupColumn: GroupColumn,
        labelKind: String,
        totalAmount: Long,
    ): List<DistributionItem> {
        val rows =
            jdbcTemplate.query(
                "SELECT ${groupColumn.sql} AS gid, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND payment_method_id IS NOT NULL " +
                    "GROUP BY ${groupColumn.sql} ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getLong("gid").takeUnless { rs.wasNull() }, rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(from),
                Date.valueOf(to),
            )
        val labels = labelReader.labelMap(LabelDomains.SALE, labelKind)
        return rows.map {
            DistributionItem(it.first, it.first?.let { id -> labels[id] } ?: ETC, it.third, it.second, percentage(it.third, totalAmount))
        }
    }

    private fun percentage(
        amount: Long,
        total: Long,
    ): Int = if (total > 0) (amount.toDouble() / total * PERCENT).roundToInt() else 0

    private fun pct(
        cur: Long,
        prev: Long,
    ): Int {
        if (prev == 0L) return if (cur > 0L) PERCENT else 0
        val v = (cur - prev) * PERCENT.toDouble() / prev
        return v.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).roundToInt()
    }

    /** distribution(...) SQL에 보간되는 그룹 컬럼 화이트리스트. 허용 집합을 타입으로 강제한다. */
    private enum class GroupColumn(
        val sql: String,
    ) {
        CATEGORY("category_id"),
        PAYMENT("payment_method_id"),
        CHANNEL("channel_id"),
    }

    private data class Aggregate(
        val total: Long,
        val count: Long,
        val unpaidAmount: Long,
        val unpaidCount: Long,
    ) {
        fun avg(): Long = if (count > 0) total / count else 0
    }

    private data class ExpenseAggregate(
        val total: Long,
        val count: Long,
    )

    private companion object {
        const val PERCENT = 100

        /** id가 null인(설정에서 삭제됐거나 미지정) 집계 버킷의 표시 라벨. */
        const val ETC = "기타"
        val EMPTY_AGGREGATE = Aggregate(0, 0, 0, 0)
        val EMPTY_EXPENSE_AGGREGATE = ExpenseAggregate(0, 0)
    }
}
