package kr.ai.flori.expenses.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.monthRange
import kr.ai.flori.expenses.dto.ExpenseCreateRequest
import kr.ai.flori.expenses.dto.ExpenseResponse
import kr.ai.flori.expenses.dto.ExpenseSuggestionsResponse
import kr.ai.flori.expenses.dto.ExpenseUpdateRequest
import kr.ai.flori.expenses.entity.Expense
import kr.ai.flori.expenses.repository.ExpenseRepository
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지출 서비스. 모든 쿼리는 TenantContext userId로 격리(HARD).
 * total_amount = unit_price * quantity 는 서버가 계산(SSOT).
 * 카테고리는 label_settings.id 간접참조 — 쓰기 시 소유권 검증, 응답 시 id→label 해석.
 */
@Service
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
    private val labelReader: LabelSettingReader,
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
        return expenses.map { toResponse(it, catMap) }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): ExpenseResponse = toResponse(load(id), categoryLabels())

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
        val expense =
            Expense(
                userId = TenantContext.currentUserId(),
                date = requireNotNull(request.date),
                itemName = requireNotNull(request.itemName),
                categoryId = categoryId,
                unitPrice = unitPrice,
                quantity = request.quantity,
                totalAmount = unitPrice * request.quantity,
                paymentMethod = requireNotNull(request.paymentMethod),
            )
        expense.cardCompany = request.cardCompany
        expense.vendor = request.vendor
        expense.memo = request.memo
        return toResponse(expenseRepository.save(expense), categoryLabels())
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
        request.paymentMethod?.let { expense.paymentMethod = it }
        request.cardCompany?.let { expense.cardCompany = it }
        request.vendor?.let { expense.vendor = it }
        request.memo?.let { expense.memo = it }
        expense.totalAmount = expense.unitPrice * expense.quantity
        return toResponse(expenseRepository.save(expense), categoryLabels())
    }

    @Transactional
    fun delete(id: Long) {
        expenseRepository.delete(load(id))
    }

    private fun categoryLabels(): Map<Long, String> = labelReader.labelMap(LabelDomains.EXPENSE, LabelKinds.CATEGORY)

    private fun toResponse(
        e: Expense,
        catMap: Map<Long, String>,
    ): ExpenseResponse = ExpenseResponse.from(e, e.categoryId?.let { catMap[it] })

    private fun load(id: Long): Expense =
        expenseRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "지출을 찾을 수 없습니다")
}
