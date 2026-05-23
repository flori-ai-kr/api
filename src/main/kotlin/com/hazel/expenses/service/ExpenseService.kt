package com.hazel.expenses.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.expenses.dto.ExpenseCreateRequest
import com.hazel.expenses.dto.ExpenseResponse
import com.hazel.expenses.dto.ExpenseSuggestionsResponse
import com.hazel.expenses.dto.ExpenseUpdateRequest
import com.hazel.expenses.entity.Expense
import com.hazel.expenses.repository.ExpenseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 지출 서비스. 모든 쿼리는 TenantContext userId로 격리(HARD).
 * total_amount = unit_price * quantity 는 서버가 계산(SSOT).
 */
@Service
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
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
        return expenses.map(ExpenseResponse::from)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ExpenseResponse = ExpenseResponse.from(load(id))

    @Transactional(readOnly = true)
    fun suggestions(): ExpenseSuggestionsResponse {
        val userId = TenantContext.currentUserId()
        return ExpenseSuggestionsResponse(
            itemNames = expenseRepository.findItemNamesByFrequency(userId),
            vendors = expenseRepository.findVendorsByFrequency(userId),
            notes = expenseRepository.findNotesByFrequency(userId),
        )
    }

    @Transactional
    fun create(request: ExpenseCreateRequest): ExpenseResponse {
        val unitPrice = requireNotNull(request.unitPrice)
        val expense =
            Expense(
                userId = TenantContext.currentUserId(),
                date = requireNotNull(request.date),
                itemName = requireNotNull(request.itemName),
                category = requireNotNull(request.category),
                unitPrice = unitPrice,
                quantity = request.quantity,
                totalAmount = unitPrice * request.quantity,
                paymentMethod = requireNotNull(request.paymentMethod),
            )
        expense.cardCompany = request.cardCompany
        expense.vendor = request.vendor
        expense.note = request.note
        return ExpenseResponse.from(expenseRepository.save(expense))
    }

    @Transactional
    fun update(
        id: UUID,
        request: ExpenseUpdateRequest,
    ): ExpenseResponse {
        val expense = load(id)
        request.date?.let { expense.date = it }
        request.itemName?.let { expense.itemName = it }
        request.category?.let { expense.category = it }
        request.unitPrice?.let { expense.unitPrice = it }
        request.quantity?.let { expense.quantity = it }
        request.paymentMethod?.let { expense.paymentMethod = it }
        request.cardCompany?.let { expense.cardCompany = it }
        request.vendor?.let { expense.vendor = it }
        request.note?.let { expense.note = it }
        expense.totalAmount = expense.unitPrice * expense.quantity
        expense.updatedAt = Instant.now()
        return ExpenseResponse.from(expenseRepository.save(expense))
    }

    @Transactional
    fun delete(id: UUID) {
        expenseRepository.delete(load(id))
    }

    private fun load(id: UUID): Expense =
        expenseRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "지출을 찾을 수 없습니다")

    private fun monthRange(month: String?): Pair<LocalDate, LocalDate>? {
        if (month.isNullOrBlank()) return null
        return when (month.length) {
            YEAR_LENGTH -> LocalDate.of(month.toInt(), 1, 1) to LocalDate.of(month.toInt(), MONTHS, LAST_DAY_DEC)
            DAY_LENGTH -> LocalDate.parse(month).let { it to it }
            else -> YearMonth.parse(month).let { it.atDay(1) to it.atEndOfMonth() }
        }
    }

    private companion object {
        const val YEAR_LENGTH = 4
        const val DAY_LENGTH = 10
        const val MONTHS = 12
        const val LAST_DAY_DEC = 31
    }
}
