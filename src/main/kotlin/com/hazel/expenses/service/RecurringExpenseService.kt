package com.hazel.expenses.service

import com.hazel.common.domain.PaymentMethods
import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.common.util.KST
import com.hazel.expenses.dto.ExpenseResponse
import com.hazel.expenses.dto.RecurringExpenseRequest
import com.hazel.expenses.dto.RecurringExpenseResponse
import com.hazel.expenses.dto.RecurringInstanceUpdateRequest
import com.hazel.expenses.entity.Expense
import com.hazel.expenses.entity.RecurringExpense
import com.hazel.expenses.entity.RecurringSkip
import com.hazel.expenses.repository.ExpenseRepository
import com.hazel.expenses.repository.RecurringExpenseRepository
import com.hazel.expenses.repository.RecurringSkipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 고정비 템플릿 서비스(CRUD·토글·빠른추가) + iOS 스타일 "이것만/이후 모두" 분기.
 * 모든 쿼리는 TenantContext userId로 격리(HARD).
 */
@Service
class RecurringExpenseService(
    private val recurringRepository: RecurringExpenseRepository,
    private val expenseRepository: ExpenseRepository,
    private val skipRepository: RecurringSkipRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<RecurringExpenseResponse> =
        recurringRepository
            .findByUserIdOrderByIsActiveDescCreatedAtDesc(TenantContext.currentUserId())
            .map(RecurringExpenseResponse::from)

    @Transactional(readOnly = true)
    fun get(id: UUID): RecurringExpenseResponse = RecurringExpenseResponse.from(loadRule(id))

    @Transactional
    fun create(request: RecurringExpenseRequest): RecurringExpenseResponse {
        val rule =
            RecurringExpense(
                userId = TenantContext.currentUserId(),
                itemName = requireNotNull(request.itemName),
                category = requireNotNull(request.category),
                unitPrice = requireNotNull(request.unitPrice),
                quantity = request.quantity,
                paymentMethod = requireValidPayment(requireNotNull(request.paymentMethod)),
                frequency = requireValidFrequency(requireNotNull(request.frequency)),
                startDate = requireNotNull(request.startDate),
            )
        applyRuleFields(rule, request)
        return RecurringExpenseResponse.from(recurringRepository.save(rule))
    }

    @Transactional
    fun update(
        id: UUID,
        request: RecurringExpenseRequest,
    ): RecurringExpenseResponse {
        val rule = loadRule(id)
        rule.itemName = requireNotNull(request.itemName)
        rule.category = requireNotNull(request.category)
        rule.unitPrice = requireNotNull(request.unitPrice)
        rule.quantity = request.quantity
        rule.paymentMethod = requireValidPayment(requireNotNull(request.paymentMethod))
        rule.frequency = requireValidFrequency(requireNotNull(request.frequency))
        rule.startDate = requireNotNull(request.startDate)
        applyRuleFields(rule, request)
        rule.updatedAt = Instant.now()
        return RecurringExpenseResponse.from(recurringRepository.save(rule))
    }

    @Transactional
    fun delete(id: UUID) {
        recurringRepository.delete(loadRule(id))
    }

    @Transactional
    fun toggleActive(
        id: UUID,
        isActive: Boolean,
    ): RecurringExpenseResponse {
        val rule = loadRule(id)
        rule.isActive = isActive
        rule.updatedAt = Instant.now()
        return RecurringExpenseResponse.from(recurringRepository.save(rule))
    }

    /** 빠른 추가: 오늘 날짜로 즉시 지출 생성. 수동 추가이므로 recurring_id 미연결(자동생성 멱등 충돌 회피). */
    @Transactional
    fun quickAdd(id: UUID): ExpenseResponse {
        val rule = loadRule(id)
        val expense =
            Expense(
                userId = rule.userId,
                date = LocalDate.now(KST),
                itemName = rule.itemName,
                category = rule.category,
                unitPrice = rule.unitPrice,
                quantity = rule.quantity,
                totalAmount = rule.unitPrice * rule.quantity,
                paymentMethod = rule.paymentMethod,
            )
        expense.vendor = rule.vendor
        expense.note = rule.note
        return ExpenseResponse.from(expenseRepository.save(expense))
    }

    /** 이것만 수정: 인스턴스만 변경하고 템플릿과 분리 표시. */
    @Transactional
    fun updateInstanceOnly(
        expenseId: UUID,
        fields: RecurringInstanceUpdateRequest,
    ) {
        val expense = loadExpense(expenseId)
        applyInstanceFields(expense, fields)
        expense.isRecurringModified = true
        expense.updatedAt = Instant.now()
        expenseRepository.save(expense)
    }

    /** 이후 모두 수정: 템플릿 + 이 인스턴스 함께 변경. */
    @Transactional
    fun updateRecurringFromInstance(
        expenseId: UUID,
        fields: RecurringInstanceUpdateRequest,
    ) {
        val expense = loadExpense(expenseId)
        val recurringId = expense.recurringId ?: throw AppException(ErrorCode.NOT_FOUND, "반복 지출 정보를 찾을 수 없습니다")
        val rule = loadRule(recurringId)
        fields.itemName?.let { rule.itemName = it }
        fields.category?.let { rule.category = it }
        fields.unitPrice?.let { rule.unitPrice = it }
        fields.quantity?.let { rule.quantity = it }
        fields.paymentMethod?.let { rule.paymentMethod = requireValidPayment(it) }
        fields.vendor?.let { rule.vendor = it }
        fields.note?.let { rule.note = it }
        rule.updatedAt = Instant.now()
        recurringRepository.save(rule)

        applyInstanceFields(expense, fields)
        expense.isRecurringModified = false
        expense.updatedAt = Instant.now()
        expenseRepository.save(expense)
    }

    /** 이것만 삭제: skip 마커 추가 후 인스턴스 삭제(자동생성 재발 방지). */
    @Transactional
    fun deleteInstanceOnly(expenseId: UUID) {
        val expense = loadExpense(expenseId)
        expense.recurringId?.let { recurringId ->
            if (!skipRepository.existsByRecurringIdAndSkipDate(recurringId, expense.date)) {
                skipRepository.save(RecurringSkip(expense.userId, recurringId, expense.date))
            }
        }
        expenseRepository.delete(expense)
    }

    /** 이후 모두 삭제: 템플릿 end_date를 이번 발생 전날로 당겨 향후 자동생성 중단 + 이 인스턴스 삭제. */
    @Transactional
    fun deleteRecurringFromInstance(expenseId: UUID) {
        val expense = loadExpense(expenseId)
        val recurringId = expense.recurringId ?: throw AppException(ErrorCode.NOT_FOUND, "반복 지출 정보를 찾을 수 없습니다")
        val rule = loadRule(recurringId)
        rule.endDate = expense.date.minusDays(1)
        rule.updatedAt = Instant.now()
        recurringRepository.save(rule)
        expenseRepository.delete(expense)
    }

    private fun applyRuleFields(
        rule: RecurringExpense,
        request: RecurringExpenseRequest,
    ) {
        rule.intervalCount = request.intervalCount
        rule.daysOfWeek = request.daysOfWeek
        rule.daysOfMonth = request.daysOfMonth
        rule.yearlyDates = request.yearlyDates
        rule.endDate = request.endDate
        rule.vendor = request.vendor
        rule.note = request.note
        rule.isActive = request.isActive
    }

    private fun applyInstanceFields(
        expense: Expense,
        fields: RecurringInstanceUpdateRequest,
    ) {
        fields.date?.let { expense.date = it }
        fields.itemName?.let { expense.itemName = it }
        fields.category?.let { expense.category = it }
        fields.unitPrice?.let { expense.unitPrice = it }
        fields.quantity?.let { expense.quantity = it }
        fields.paymentMethod?.let { expense.paymentMethod = requireValidPayment(it) }
        fields.vendor?.let { expense.vendor = it }
        fields.note?.let { expense.note = it }
        expense.totalAmount = expense.unitPrice * expense.quantity
    }

    private fun loadRule(id: UUID): RecurringExpense =
        recurringRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "고정비를 찾을 수 없습니다")

    private fun loadExpense(id: UUID): Expense =
        expenseRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "지출을 찾을 수 없습니다")

    private fun requireValidFrequency(value: String): String {
        if (value !in FREQUENCIES) throw AppException(ErrorCode.VALIDATION, "올바르지 않은 반복 주기입니다")
        return value
    }

    private fun requireValidPayment(value: String): String {
        if (value !in PaymentMethods.EXPENSE) throw AppException(ErrorCode.VALIDATION, "올바르지 않은 결제방식입니다")
        return value
    }

    private companion object {
        val FREQUENCIES = setOf("weekly", "monthly", "yearly")
    }
}
