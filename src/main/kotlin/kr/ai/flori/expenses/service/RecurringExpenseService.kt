package kr.ai.flori.expenses.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.expenses.dto.RecurringExpenseRequest
import kr.ai.flori.expenses.dto.RecurringExpenseResponse
import kr.ai.flori.expenses.dto.RecurringInstanceUpdateRequest
import kr.ai.flori.expenses.entity.Expense
import kr.ai.flori.expenses.entity.RecurringExpense
import kr.ai.flori.expenses.entity.RecurringSkip
import kr.ai.flori.expenses.repository.ExpenseRepository
import kr.ai.flori.expenses.repository.RecurringExpenseRepository
import kr.ai.flori.expenses.repository.RecurringSkipRepository
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 고정비 템플릿 서비스(CRUD·토글) + iOS 스타일 "이것만/이후 모두" 분기.
 * 모든 쿼리는 TenantContext userId로 격리(HARD).
 * 카테고리는 label_settings.id 간접참조 — 쓰기 시 소유권 검증, 응답 시 id→label 해석.
 */
@Service
class RecurringExpenseService(
    private val recurringRepository: RecurringExpenseRepository,
    private val expenseRepository: ExpenseRepository,
    private val skipRepository: RecurringSkipRepository,
    private val labelReader: LabelSettingReader,
) {
    @Transactional(readOnly = true)
    fun list(): List<RecurringExpenseResponse> {
        val catMap = categoryLabels()
        val payMap = paymentLabels()
        return recurringRepository
            .findByUserIdOrderByIsActiveDescCreatedAtDesc(TenantContext.currentUserId())
            .map { ruleResponse(it, catMap, payMap) }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): RecurringExpenseResponse = ruleResponse(loadRule(id), categoryLabels(), paymentLabels())

    @Transactional
    fun create(request: RecurringExpenseRequest): RecurringExpenseResponse {
        val rule =
            RecurringExpense(
                userId = TenantContext.currentUserId(),
                itemName = requireNotNull(request.itemName),
                categoryId =
                    labelReader.requireOwned(
                        requireNotNull(request.categoryId),
                        LabelDomains.EXPENSE,
                        LabelKinds.CATEGORY,
                    ),
                unitPrice = requireNotNull(request.unitPrice),
                quantity = request.quantity,
                paymentMethodId =
                    labelReader.requireOwned(
                        requireNotNull(request.paymentMethodId),
                        LabelDomains.EXPENSE,
                        LabelKinds.PAYMENT,
                    ),
                frequency = requireValidFrequency(requireNotNull(request.frequency)),
                startDate = requireNotNull(request.startDate),
            )
        applyRuleFields(rule, request)
        return ruleResponse(recurringRepository.save(rule), categoryLabels(), paymentLabels())
    }

    @Transactional
    fun update(
        id: Long,
        request: RecurringExpenseRequest,
    ): RecurringExpenseResponse {
        val rule = loadRule(id)
        rule.itemName = requireNotNull(request.itemName)
        rule.categoryId =
            labelReader.requireOwned(requireNotNull(request.categoryId), LabelDomains.EXPENSE, LabelKinds.CATEGORY)
        rule.unitPrice = requireNotNull(request.unitPrice)
        rule.quantity = request.quantity
        rule.paymentMethodId =
            labelReader.requireOwned(requireNotNull(request.paymentMethodId), LabelDomains.EXPENSE, LabelKinds.PAYMENT)
        rule.frequency = requireValidFrequency(requireNotNull(request.frequency))
        rule.startDate = requireNotNull(request.startDate)
        applyRuleFields(rule, request)
        return ruleResponse(recurringRepository.save(rule), categoryLabels(), paymentLabels())
    }

    @Transactional
    fun delete(id: Long) {
        val rule = loadRule(id)
        // FK 미사용: skip 기록은 삭제(CASCADE 대체), 자동생성된 지출은 recurring_id만 NULL(지출 보존).
        skipRepository.deleteByUserIdAndRecurringId(rule.userId, id)
        expenseRepository.clearRecurringReference(rule.userId, id)
        recurringRepository.delete(rule)
    }

    @Transactional
    fun toggleActive(
        id: Long,
        isActive: Boolean,
    ): RecurringExpenseResponse {
        val rule = loadRule(id)
        rule.isActive = isActive
        return ruleResponse(recurringRepository.save(rule), categoryLabels(), paymentLabels())
    }

    /** 이것만 수정: 인스턴스만 변경하고 템플릿과 분리 표시. */
    @Transactional
    fun updateInstanceOnly(
        expenseId: Long,
        fields: RecurringInstanceUpdateRequest,
    ) {
        val expense = loadExpense(expenseId)
        applyInstanceFields(expense, fields)
        expense.isRecurringModified = true
        expenseRepository.save(expense)
    }

    /** 이후 모두 수정: 템플릿 + 이 인스턴스 함께 변경. */
    @Transactional
    fun updateRecurringFromInstance(
        expenseId: Long,
        fields: RecurringInstanceUpdateRequest,
    ) {
        val expense = loadExpense(expenseId)
        val recurringId = expense.recurringId ?: throw AppException(CommonErrorCode.NOT_FOUND, "반복 지출 정보를 찾을 수 없습니다")
        val rule = loadRule(recurringId)
        fields.itemName?.let { rule.itemName = it }
        fields.categoryId?.let { rule.categoryId = labelReader.requireOwned(it, LabelDomains.EXPENSE, LabelKinds.CATEGORY) }
        fields.unitPrice?.let { rule.unitPrice = it }
        fields.quantity?.let { rule.quantity = it }
        fields.paymentMethodId?.let {
            rule.paymentMethodId = labelReader.requireOwned(it, LabelDomains.EXPENSE, LabelKinds.PAYMENT)
        }
        fields.vendor?.let { rule.vendor = it }
        fields.memo?.let { rule.memo = it }
        recurringRepository.save(rule)

        applyInstanceFields(expense, fields)
        expense.isRecurringModified = false
        expenseRepository.save(expense)
    }

    /** 이것만 삭제: skip 마커 추가 후 인스턴스 삭제(자동생성 재발 방지). */
    @Transactional
    fun deleteInstanceOnly(expenseId: Long) {
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
    fun deleteRecurringFromInstance(expenseId: Long) {
        val expense = loadExpense(expenseId)
        val recurringId = expense.recurringId ?: throw AppException(CommonErrorCode.NOT_FOUND, "반복 지출 정보를 찾을 수 없습니다")
        val rule = loadRule(recurringId)
        rule.endDate = expense.date.minusDays(1)
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
        rule.memo = request.memo
        rule.isActive = request.isActive
    }

    private fun applyInstanceFields(
        expense: Expense,
        fields: RecurringInstanceUpdateRequest,
    ) {
        fields.date?.let { expense.date = it }
        fields.itemName?.let { expense.itemName = it }
        fields.categoryId?.let {
            expense.categoryId = labelReader.requireOwned(it, LabelDomains.EXPENSE, LabelKinds.CATEGORY)
        }
        fields.unitPrice?.let { expense.unitPrice = it }
        fields.quantity?.let { expense.quantity = it }
        fields.paymentMethodId?.let {
            expense.paymentMethodId = labelReader.requireOwned(it, LabelDomains.EXPENSE, LabelKinds.PAYMENT)
        }
        fields.vendor?.let { expense.vendor = it }
        fields.memo?.let { expense.memo = it }
        expense.totalAmount = expense.unitPrice * expense.quantity
    }

    private fun categoryLabels(): Map<Long, String> = labelReader.labelMap(LabelDomains.EXPENSE, LabelKinds.CATEGORY)

    private fun paymentLabels(): Map<Long, String> = labelReader.labelMap(LabelDomains.EXPENSE, LabelKinds.PAYMENT)

    private fun ruleResponse(
        rule: RecurringExpense,
        catMap: Map<Long, String>,
        payMap: Map<Long, String>,
    ): RecurringExpenseResponse =
        RecurringExpenseResponse.from(
            rule,
            rule.categoryId?.let { catMap[it] },
            rule.paymentMethodId?.let { payMap[it] },
        )

    private fun loadRule(id: Long): RecurringExpense =
        recurringRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "고정비를 찾을 수 없습니다")

    private fun loadExpense(id: Long): Expense =
        expenseRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "지출을 찾을 수 없습니다")

    private fun requireValidFrequency(value: String): String {
        if (value !in FREQUENCIES) throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 반복 주기입니다")
        return value
    }

    private companion object {
        val FREQUENCIES = setOf("weekly", "monthly", "yearly")
    }
}
