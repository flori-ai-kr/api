package kr.ai.flori.statistics.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.statistics.dto.CustomerKpi
import kr.ai.flori.statistics.dto.CustomerNewPoint
import kr.ai.flori.statistics.dto.CustomerStatisticsResponse
import kr.ai.flori.statistics.dto.GenderCount
import kr.ai.flori.statistics.dto.GradeCount
import kr.ai.flori.statistics.dto.TopCustomer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

/**
 * 고객 통계. 신규/재방문 판정은 customer_phone 기준이며(대시보드와 동일 규약), 구매는 미수 제외
 * (payment_method_id IS NOT NULL)한 매출만 집계한다. 등급·성별 분포는 기간과 무관하게 전체 고객을 대상으로 한다.
 * 증감(delta)은 직전 동일 길이 기간과 비교한다.
 */
@Service
class CustomerStatisticsService(
    private val jdbcTemplate: JdbcTemplate,
    private val support: StatisticsSupport,
) {
    @Transactional(readOnly = true)
    fun customerStatistics(
        from: LocalDate,
        to: LocalDate,
    ): CustomerStatisticsResponse {
        if (from.isAfter(to)) throw AppException(CommonErrorCode.VALIDATION, "from must not be after to")
        val userId = TenantContext.currentUserId()
        val prev = support.previousPeriod(from, to)

        val cur = customerCounts(userId, from, to)
        val prevCounts = customerCounts(userId, prev.from, prev.to)

        val kpi =
            CustomerKpi(
                total = cur.total,
                newCustomers = cur.newCustomers,
                newDelta = cur.newCustomers - prevCounts.newCustomers,
                returningCustomers = cur.returning,
                returningDelta = cur.returning - prevCounts.returning,
                returningRatePct = if (cur.total > 0) support.percentage(cur.returning, cur.total) else 0,
            )

        return CustomerStatisticsResponse(
            kpi = kpi,
            timeseries = newCustomerTimeseries(userId, from, to),
            gradeDistribution = gradeDistribution(userId),
            genderDistribution = genderDistribution(userId),
            topCustomers = topCustomers(userId, from, to),
        )
    }

    /** 기간 내 구매(미수 제외) distinct 고객 수 + 재방문(기간 이전 선구매 존재) 수. 신규 = total - returning. */
    private fun customerCounts(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): CustomerCounts {
        val total =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT customer_phone) FROM sales " +
                    "WHERE user_id = ?::bigint AND date BETWEEN ? AND ? " +
                    "AND payment_method_id IS NOT NULL AND customer_phone IS NOT NULL",
                Long::class.java,
                userId,
                Date.valueOf(from),
                Date.valueOf(to),
            ) ?: 0
        val returning =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT m.customer_phone) FROM sales m " +
                    "WHERE m.user_id = ?::bigint AND m.date BETWEEN ? AND ? " +
                    "AND m.payment_method_id IS NOT NULL AND m.customer_phone IS NOT NULL " +
                    "AND EXISTS (SELECT 1 FROM sales p WHERE p.user_id = m.user_id " +
                    "AND p.customer_phone = m.customer_phone AND p.payment_method_id IS NOT NULL AND p.date < ?)",
                Long::class.java,
                userId,
                Date.valueOf(from),
                Date.valueOf(to),
                Date.valueOf(from),
            ) ?: 0
        return CustomerCounts(total, total - returning, returning)
    }

    /** 일별 신규 고객 시계열(그날이 해당 고객의 전체 최초 구매일인 경우만). 구매 있는 날만, 일자 오름차순. */
    private fun newCustomerTimeseries(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<CustomerNewPoint> =
        jdbcTemplate.query(
            """
            SELECT s.date AS d, COUNT(DISTINCT s.customer_phone) AS cnt
            FROM sales s
            WHERE s.user_id = ?::bigint AND s.date BETWEEN ? AND ?
              AND s.payment_method_id IS NOT NULL AND s.customer_phone IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM sales p WHERE p.user_id = s.user_id
                  AND p.customer_phone = s.customer_phone AND p.payment_method_id IS NOT NULL AND p.date < s.date
              )
            GROUP BY s.date ORDER BY s.date
            """.trimIndent(),
            { rs, _ -> CustomerNewPoint(rs.getDate("d").toLocalDate(), rs.getLong("cnt")) },
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
        )

    /** 등급별 고객 분포(전체 고객, 기간 무관). */
    private fun gradeDistribution(userId: Long): List<GradeCount> =
        jdbcTemplate.query(
            "SELECT COALESCE(grade, 'new') AS grade, COUNT(*) AS cnt " +
                "FROM customers WHERE user_id = ?::bigint GROUP BY COALESCE(grade, 'new') ORDER BY cnt DESC",
            { rs, _ -> GradeCount(rs.getString("grade"), rs.getLong("cnt")) },
            userId,
        )

    /** 성별 고객 분포(전체 고객, 기간 무관). gender는 NULL일 수 있다. */
    private fun genderDistribution(userId: Long): List<GenderCount> =
        jdbcTemplate.query(
            "SELECT gender, COUNT(*) AS cnt FROM customers WHERE user_id = ?::bigint GROUP BY gender ORDER BY cnt DESC",
            { rs, _ -> GenderCount(rs.getString("gender"), rs.getLong("cnt")) },
            userId,
        )

    /**
     * 기간 내 구매(미수 제외) TOP 고객(금액 내림차순 10명). customer_id가 있으면 그 기준으로,
     * 없으면 customer_phone 기준으로 묶는다. 등급/이름/전화는 customers를 LEFT JOIN해 정규화하고
     * 매칭 고객이 없으면 등급은 'new'.
     */
    private fun topCustomers(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<TopCustomer> =
        jdbcTemplate.query(
            """
            SELECT
              c.id AS cid,
              COALESCE(c.name, MAX(s.customer_name)) AS name,
              COALESCE(c.phone, s.customer_phone) AS phone,
              COALESCE(c.grade, ?) AS grade,
              COUNT(*) AS cnt,
              SUM(s.amount) AS amount
            FROM sales s
            LEFT JOIN customers c ON c.id = s.customer_id AND c.user_id = s.user_id
            WHERE s.user_id = ?::bigint AND s.date BETWEEN ? AND ? AND s.payment_method_id IS NOT NULL
              AND (s.customer_id IS NOT NULL OR s.customer_phone IS NOT NULL)
            GROUP BY COALESCE(s.customer_id::text, s.customer_phone), c.id, c.name, c.phone, c.grade, s.customer_phone
            ORDER BY amount DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                TopCustomer(
                    customerId = rs.getLong("cid").takeUnless { rs.wasNull() },
                    name = rs.getString("name") ?: "",
                    phone = rs.getString("phone") ?: "",
                    grade = rs.getString("grade") ?: DEFAULT_GRADE,
                    purchaseCount = rs.getLong("cnt"),
                    totalAmount = rs.getLong("amount"),
                )
            },
            DEFAULT_GRADE,
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
            TOP_CUSTOMERS_LIMIT,
        )

    private data class CustomerCounts(
        val total: Long,
        val newCustomers: Long,
        val returning: Long,
    )

    private companion object {
        /** TOP 고객 노출 상한. */
        const val TOP_CUSTOMERS_LIMIT = 10

        /** customers 매칭이 없는 집계 버킷의 기본 등급(customers.grade DEFAULT와 동일). */
        const val DEFAULT_GRADE = "new"
    }
}
