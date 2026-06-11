package kr.ai.flori.expenses.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import kr.ai.flori.common.util.monthRange
import kr.ai.flori.expenses.dto.ExpenseCategorySlice
import kr.ai.flori.expenses.dto.ExpenseCreateRequest
import kr.ai.flori.expenses.dto.ExpensePageResponse
import kr.ai.flori.expenses.dto.ExpenseResponse
import kr.ai.flori.expenses.dto.ExpenseSuggestionsResponse
import kr.ai.flori.expenses.dto.ExpenseUpdateRequest
import kr.ai.flori.expenses.dto.ExpensesSummaryResponse
import kr.ai.flori.expenses.entity.Expense
import kr.ai.flori.expenses.repository.ExpenseRepository
import kr.ai.flori.expenses.repository.ExpenseSpecifications
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

/**
 * 지출 서비스. 모든 쿼리는 TenantContext userId로 격리(HARD).
 * total_amount = unit_price * quantity 는 서버가 계산(SSOT).
 * 카테고리는 label_settings.id 간접참조 — 쓰기 시 소유권 검증, 응답 시 id→label 해석.
 */
@Service
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
    private val labelReader: LabelSettingReader,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun list(month: String?): List<ExpenseResponse> {
        val userId = TenantContext.currentUserId()
        val range = monthRange(month)
        val expenses =
            if (range == null) {
                expenseRepository.findByUserIdOrderByDateDesc(userId)
            } else {
                expenseRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, range.first, range.second)
            }
        val catMap = categoryLabels()
        val payMap = paymentLabels()
        return expenses.map { toResponse(it, catMap, payMap) }
    }

    @Suppress("LongParameterList")
    @Transactional(readOnly = true)
    fun listPaged(
        month: String?,
        startDate: String?,
        endDate: String?,
        offset: Int,
        limit: Int,
        categories: List<Long>?,
        payments: List<Long>?,
        search: String?,
    ): ExpensePageResponse {
        val userId = TenantContext.currentUserId()
        val spec = ExpenseSpecifications.filter(userId, month, startDate, endDate, categories, payments, search)
        val sort = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt"))
        val pageable = Paging.offsetLimit(offset, limit, MAX_LIMIT, sort)
        val page = expenseRepository.findAll(spec, pageable)
        val catMap = categoryLabels()
        val payMap = paymentLabels()
        return ExpensePageResponse(page.content.map { toResponse(it, catMap, payMap) }, page.hasNext())
    }

    @Suppress("LongParameterList")
    @Transactional(readOnly = true)
    fun summary(
        month: String?,
        startDate: String?,
        endDate: String?,
        categories: List<Long>?,
        payments: List<Long>?,
        search: String?,
    ): ExpensesSummaryResponse {
        val userId = TenantContext.currentUserId()

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

    @Transactional(readOnly = true)
    fun get(id: Long): ExpenseResponse = toResponse(load(id), categoryLabels(), paymentLabels())

    @Transactional(readOnly = true)
    fun suggestions(): ExpenseSuggestionsResponse {
        val userId = TenantContext.currentUserId()
        return ExpenseSuggestionsResponse(
            itemNames = expenseRepository.findItemNamesByFrequency(userId),
            vendors = expenseRepository.findVendorsByFrequency(userId),
            memos = expenseRepository.findMemosByFrequency(userId),
        )
    }

    @Transactional
    fun create(request: ExpenseCreateRequest): ExpenseResponse {
        val unitPrice = requireNotNull(request.unitPrice)
        val categoryId =
            labelReader.requireOwned(requireNotNull(request.categoryId), LabelDomains.EXPENSE, LabelKinds.CATEGORY)
        val paymentMethodId =
            labelReader.requireOwned(requireNotNull(request.paymentMethodId), LabelDomains.EXPENSE, LabelKinds.PAYMENT)
        val expense =
            Expense(
                userId = TenantContext.currentUserId(),
                date = requireNotNull(request.date),
                itemName = requireNotNull(request.itemName),
                categoryId = categoryId,
                unitPrice = unitPrice,
                quantity = request.quantity,
                totalAmount = unitPrice * request.quantity,
                paymentMethodId = paymentMethodId,
            )
        expense.cardCompany = request.cardCompany
        expense.vendor = request.vendor
        expense.memo = request.memo
        return toResponse(expenseRepository.save(expense), categoryLabels(), paymentLabels())
    }

    @Transactional
    fun update(
        id: Long,
        request: ExpenseUpdateRequest,
    ): ExpenseResponse {
        val expense = load(id)
        request.date?.let { expense.date = it }
        request.itemName?.let { expense.itemName = it }
        request.categoryId?.let {
            expense.categoryId = labelReader.requireOwned(it, LabelDomains.EXPENSE, LabelKinds.CATEGORY)
        }
        request.unitPrice?.let { expense.unitPrice = it }
        request.quantity?.let { expense.quantity = it }
        request.paymentMethodId?.let {
            expense.paymentMethodId = labelReader.requireOwned(it, LabelDomains.EXPENSE, LabelKinds.PAYMENT)
        }
        request.cardCompany?.let { expense.cardCompany = it }
        request.vendor?.let { expense.vendor = it }
        request.memo?.let { expense.memo = it }
        expense.totalAmount = expense.unitPrice * expense.quantity
        return toResponse(expenseRepository.save(expense), categoryLabels(), paymentLabels())
    }

    @Transactional
    fun delete(id: Long) {
        expenseRepository.delete(load(id))
    }

    private fun categoryLabels(): Map<Long, String> = labelReader.labelMap(LabelDomains.EXPENSE, LabelKinds.CATEGORY)

    private fun paymentLabels(): Map<Long, String> = labelReader.labelMap(LabelDomains.EXPENSE, LabelKinds.PAYMENT)

    private fun toResponse(
        e: Expense,
        catMap: Map<Long, String>,
        payMap: Map<Long, String>,
    ): ExpenseResponse = ExpenseResponse.from(e, e.categoryId?.let { catMap[it] }, e.paymentMethodId?.let { payMap[it] })

    private fun load(id: Long): Expense =
        expenseRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "지출을 찾을 수 없습니다")

    companion object {
        private const val MAX_LIMIT = 100
        private const val SEARCH_FIELD_COUNT = 3
        private val ALLOWED_SUMMARY_COLUMNS = setOf("e.category_id", "e.payment_method_id")
        private const val TOTAL_SELECT =
            "SELECT COALESCE(SUM(e.total_amount), 0) AS total, COUNT(*) AS cnt FROM expenses e WHERE e.user_id = ?"
        private val BY_CATEGORY_SELECT =
            """
            SELECT ls.id AS cat_id, COALESCE(ls.label, '미분류') AS cat_label,
                   COALESCE(SUM(e.total_amount), 0) AS amount
            FROM expenses e LEFT JOIN label_settings ls ON ls.id = e.category_id AND ls.user_id = e.user_id
            WHERE e.user_id = ?
            """.trimIndent()
    }
}
