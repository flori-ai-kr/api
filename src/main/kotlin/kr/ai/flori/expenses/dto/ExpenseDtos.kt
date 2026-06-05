package kr.ai.flori.expenses.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.expenses.entity.Expense
import java.time.Instant
import java.time.LocalDate

data class ExpenseCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    @field:NotBlank(message = "물품명은 필수입니다")
    @field:Size(max = FieldLimits.ITEM_NAME, message = "물품명이 너무 깁니다")
    val itemName: String?,
    @field:NotNull(message = "카테고리는 필수입니다")
    val categoryId: Long?,
    @field:NotNull @field:Min(0, message = "단가는 0 이상이어야 합니다")
    val unitPrice: Int?,
    @field:Min(1, message = "수량은 1 이상이어야 합니다")
    val quantity: Int = 1,
    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String?,
    @field:Size(max = FieldLimits.CARD_COMPANY, message = "카드사명이 너무 깁니다")
    val cardCompany: String? = null,
    @field:Size(max = FieldLimits.VENDOR, message = "거래처가 너무 깁니다")
    val vendor: String? = null,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
)

data class ExpenseUpdateRequest(
    val date: LocalDate? = null,
    @field:Size(max = FieldLimits.ITEM_NAME, message = "물품명이 너무 깁니다")
    val itemName: String? = null,
    val categoryId: Long? = null,
    @field:Min(0, message = "단가는 0 이상이어야 합니다")
    val unitPrice: Int? = null,
    @field:Min(1, message = "수량은 1 이상이어야 합니다")
    val quantity: Int? = null,
    val paymentMethod: String? = null,
    @field:Size(max = FieldLimits.CARD_COMPANY, message = "카드사명이 너무 깁니다")
    val cardCompany: String? = null,
    @field:Size(max = FieldLimits.VENDOR, message = "거래처가 너무 깁니다")
    val vendor: String? = null,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
)

data class ExpenseResponse(
    val id: Long,
    val date: LocalDate,
    val itemName: String,
    val categoryId: Long?,
    val categoryLabel: String?,
    val unitPrice: Int,
    val quantity: Int,
    val totalAmount: Int,
    val paymentMethod: String,
    val cardCompany: String?,
    val vendor: String?,
    val memo: String?,
    val recurringId: Long?,
    val isRecurringModified: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            e: Expense,
            categoryLabel: String?,
        ): ExpenseResponse =
            ExpenseResponse(
                id = requireNotNull(e.id),
                date = e.date,
                itemName = e.itemName,
                categoryId = e.categoryId,
                categoryLabel = categoryLabel,
                unitPrice = e.unitPrice,
                quantity = e.quantity,
                totalAmount = e.totalAmount,
                paymentMethod = e.paymentMethod,
                cardCompany = e.cardCompany,
                vendor = e.vendor,
                memo = e.memo,
                recurringId = e.recurringId,
                isRecurringModified = e.isRecurringModified,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
            )
    }
}

data class ExpenseSuggestionsResponse(
    val itemNames: List<String>,
    val vendors: List<String>,
    val memos: List<String>,
)
