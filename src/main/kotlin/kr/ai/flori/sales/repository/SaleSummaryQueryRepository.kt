package kr.ai.flori.sales.repository

import kr.ai.flori.common.util.monthRange
import kr.ai.flori.sales.dto.SalesSummaryResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.LocalDate

/**
 * 매출 요약(페이지네이션 무관 전체 합산) 집계 전용 리포지토리.
 * GET /sales 와 동일한 필터 규약(SaleSpecifications.filter)으로 동적 WHERE 를 구성해 DB 집계(SUM/FILTER).
 * total/count는 전체(미수 포함), 명세 버킷(card/naverpay/transfer/cash)은 결제수단 라벨 value 기준.
 */
@Repository
class SaleSummaryQueryRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Suppress("LongParameterList")
    fun summarize(
        userId: Long,
        month: String?,
        startDate: String?,
        endDate: String?,
        categories: List<Long>?,
        payments: List<Long>?,
        channels: List<Long>?,
        search: String?,
    ): SalesSummaryResponse {
        val sql = StringBuilder(SUMMARY_SELECT)
        val params = mutableListOf<Any>(userId)
        appendFilters(sql, params, month, startDate, endDate, categories, payments, channels, search)
        return jdbcTemplate.queryForObject(
            sql.toString(),
            { rs, _ ->
                SalesSummaryResponse(
                    rs.getLong("total"),
                    rs.getLong("card"),
                    rs.getLong("naverpay"),
                    rs.getLong("transfer"),
                    rs.getLong("cash"),
                    rs.getLong("cnt"),
                )
            },
            *params.toTypedArray(),
        ) ?: EMPTY_SUMMARY
    }

    /** 동적 WHERE 절을 SaleSpecifications.filter 와 동일한 규약으로 구성한다. */
    @Suppress("LongParameterList")
    private fun appendFilters(
        sql: StringBuilder,
        params: MutableList<Any>,
        month: String?,
        startDate: String?,
        endDate: String?,
        categories: List<Long>?,
        payments: List<Long>?,
        channels: List<Long>?,
        search: String?,
    ) {
        if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
            sql.append(" AND s.date BETWEEN ? AND ?")
            params.add(Date.valueOf(LocalDate.parse(startDate)))
            params.add(Date.valueOf(LocalDate.parse(endDate)))
        } else {
            monthRange(month)?.let { (start, end) ->
                sql.append(" AND s.date BETWEEN ? AND ?")
                params.add(Date.valueOf(start))
                params.add(Date.valueOf(end))
            }
        }
        appendInClause(sql, params, "s.category_id", categories)
        appendInClause(sql, params, "s.payment_method_id", payments)
        appendInClause(sql, params, "s.channel_id", channels)
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase().replace("%", "\\%").replace("_", "\\_")}%"
            sql.append(
                " AND (lower(s.customer_name) LIKE ? OR lower(s.memo) LIKE ?)",
            )
            repeat(SEARCH_FIELD_COUNT) { params.add(pattern) }
        }
    }

    private fun appendInClause(
        sql: StringBuilder,
        params: MutableList<Any>,
        column: String,
        values: List<*>?,
    ) {
        // column은 호출부의 컴파일타임 상수만 허용(식별자는 바인딩 불가) — 미래의 사용자 입력 주입을 원천 차단.
        require(column in ALLOWED_SUMMARY_COLUMNS) { "허용되지 않은 집계 컬럼: $column" }
        if (values.isNullOrEmpty()) return
        sql
            .append(" AND ")
            .append(column)
            .append(" IN (")
            .append(values.joinToString(",") { "?" })
            .append(")")
        values.forEach { params.add(it as Any) }
    }

    private companion object {
        /** summary 검색 패턴이 적용되는 컬럼 수(customer_name·memo). */
        const val SEARCH_FIELD_COUNT = 2

        /** appendInClause가 SQL에 직접 끼워 넣어도 안전한 컬럼 화이트리스트(식별자 주입 방어). */
        val ALLOWED_SUMMARY_COLUMNS = setOf("s.category_id", "s.payment_method_id", "s.channel_id")
        val EMPTY_SUMMARY = SalesSummaryResponse(0, 0, 0, 0, 0, 0)

        // 고정 버킷(card/naverpay/transfer/cash)은 결제수단 라벨의 value 로 매핑(label_settings JOIN).
        // total/cnt 는 전체(미수 포함), 버킷은 해당 value 만 합산.
        val SUMMARY_SELECT =
            """
            SELECT
              COALESCE(SUM(s.amount) FILTER (WHERE s.payment_method_id IS NOT NULL), 0) AS total,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'card'), 0) AS card,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'naverpay'), 0) AS naverpay,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'transfer'), 0) AS transfer,
              COALESCE(SUM(s.amount) FILTER (WHERE ls.value = 'cash'), 0) AS cash,
              COUNT(*) AS cnt
            FROM sales s LEFT JOIN label_settings ls ON ls.id = s.payment_method_id AND ls.user_id = s.user_id
            WHERE s.user_id = ?::bigint
            """.trimIndent()
    }
}
