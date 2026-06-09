package kr.ai.flori.statistics.service

import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import kr.ai.flori.statistics.dto.DistributionItem
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
            categoryDistribution = distribution(userId, from, to, "category_id", LabelKinds.CATEGORY, cur.total),
            paymentDistribution = distribution(userId, from, to, "payment_method_id", LabelKinds.PAYMENT, cur.total),
            channelDistribution = distribution(userId, from, to, "channel_id", LabelKinds.CHANNEL, cur.total),
        )
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
        groupColumn: String,
        kind: String,
        totalAmount: Long,
    ): List<DistributionItem> {
        val rows =
            jdbcTemplate.query(
                "SELECT $groupColumn AS gid, COUNT(*) AS cnt, SUM(amount) AS amount " +
                    "FROM sales WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND payment_method_id IS NOT NULL " +
                    "GROUP BY $groupColumn ORDER BY amount DESC",
                { rs, _ -> Triple(rs.getLong("gid").takeUnless { rs.wasNull() }, rs.getLong("cnt"), rs.getLong("amount")) },
                userId,
                Date.valueOf(from),
                Date.valueOf(to),
            )
        val labels = labelReader.labelMap(LabelDomains.SALE, kind)
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
    ): Int = if (prev == 0L) (if (cur > 0L) PERCENT else 0) else Math.round((cur - prev) * PERCENT.toDouble() / prev).toInt()

    private data class Aggregate(
        val total: Long,
        val count: Long,
        val unpaidAmount: Long,
        val unpaidCount: Long,
    ) {
        fun avg(): Long = if (count > 0) total / count else 0
    }

    private companion object {
        const val PERCENT = 100

        /** id가 null인(설정에서 삭제됐거나 미지정) 집계 버킷의 표시 라벨. */
        const val ETC = "기타"
        val EMPTY_AGGREGATE = Aggregate(0, 0, 0, 0)
    }
}
