package kr.ai.flori.expenses.repository

import kr.ai.flori.common.util.monthRange
import kr.ai.flori.expenses.dto.ExpenseCategorySlice
import kr.ai.flori.expenses.dto.ExpensesSummaryResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.LocalDate

/**
 * 지출 요약(총액·건수 + 카테고리별 합계) 집계 전용 리포지토리.
 * GET /expenses 와 동일한 필터 규약(ExpenseSpecifications.filter)으로 동적 WHERE 를 구성한다.
 */
@Repository
class ExpenseSummaryQueryRepository(
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
        search: String?,
    ): ExpensesSummaryResponse {
        val totalSql = StringBuilder(TOTAL_SELECT)
        val totalParams = mutableListOf<Any>(userId)
        appendFilters(totalSql, totalParams, month, startDate, endDate, categories, payments, search)
        val totals =
            jdbcTemplate.queryForObject(
                totalSql.toString(),
                { rs, _ -> rs.getLong("total") to rs.getLong("cnt") },
                *totalParams.toTypedArray(),
            ) ?: (0L to 0L)

        val catSql = StringBuilder(BY_CATEGORY_SELECT)
        val catParams = mutableListOf<Any>(userId)
        appendFilters(catSql, catParams, month, startDate, endDate, categories, payments, search)
        catSql.append(" GROUP BY ls.id, ls.label ORDER BY amount DESC")
        val slices =
            jdbcTemplate.query(
                catSql.toString(),
                { rs, _ ->
                    ExpenseCategorySlice(
                        (rs.getObject("cat_id") as? Number)?.toLong(),
                        rs.getString("cat_label") ?: "미분류",
                        rs.getLong("amount"),
                    )
                },
                *catParams.toTypedArray(),
            )
        return ExpensesSummaryResponse(totals.first, totals.second, slices)
    }

    @Suppress("LongParameterList")
    private fun appendFilters(
        sql: StringBuilder,
        params: MutableList<Any>,
        month: String?,
        startDate: String?,
        endDate: String?,
        categories: List<Long>?,
        payments: List<Long>?,
        search: String?,
    ) {
        if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
            sql.append(" AND e.date BETWEEN ? AND ?")
            params.add(Date.valueOf(LocalDate.parse(startDate)))
            params.add(Date.valueOf(LocalDate.parse(endDate)))
        } else {
            monthRange(month)?.let { (start, end) ->
                sql.append(" AND e.date BETWEEN ? AND ?")
                params.add(Date.valueOf(start))
                params.add(Date.valueOf(end))
            }
        }
        appendInClause(sql, params, "e.category_id", categories)
        appendInClause(sql, params, "e.payment_method_id", payments)
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase().replace("%", "\\%").replace("_", "\\_")}%"
            sql.append(" AND (lower(e.item_name) LIKE ? OR lower(e.vendor) LIKE ? OR lower(e.memo) LIKE ?)")
            repeat(SEARCH_FIELD_COUNT) { params.add(pattern) }
        }
    }

    private fun appendInClause(
        sql: StringBuilder,
        params: MutableList<Any>,
        column: String,
        values: List<*>?,
    ) {
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
        const val SEARCH_FIELD_COUNT = 3
        val ALLOWED_SUMMARY_COLUMNS = setOf("e.category_id", "e.payment_method_id")
        const val TOTAL_SELECT =
            "SELECT COALESCE(SUM(e.total_amount), 0) AS total, COUNT(*) AS cnt FROM expenses e WHERE e.user_id = ?"
        val BY_CATEGORY_SELECT =
            """
            SELECT ls.id AS cat_id, COALESCE(ls.label, '미분류') AS cat_label,
                   COALESCE(SUM(e.total_amount), 0) AS amount
            FROM expenses e LEFT JOIN label_settings ls ON ls.id = e.category_id AND ls.user_id = e.user_id
            WHERE e.user_id = ?
            """.trimIndent()
    }
}
