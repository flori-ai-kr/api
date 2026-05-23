package com.hazel.expenses.dto

import com.hazel.expenses.entity.Expense
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ExpenseCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    @field:NotBlank(message = "물품명은 필수입니다")
    val itemName: String?,
    @field:NotBlank(message = "카테고리는 필수입니다")
    val category: String?,
    @field:NotNull @field:Min(0, message = "단가는 0 이상이어야 합니다")
    val unitPrice: Int?,
    @field:Min(1, message = "수량은 1 이상이어야 합니다")
    val quantity: Int = 1,
    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String?,
    val cardCompany: String? = null,
    val vendor: String? = null,
    val note: String? = null,
)

data class ExpenseUpdateRequest(
    val date: LocalDate? = null,
    val itemName: String? = null,
    val category: String? = null,
    @field:Min(0, message = "단가는 0 이상이어야 합니다")
    val unitPrice: Int? = null,
    @field:Min(1, message = "수량은 1 이상이어야 합니다")
    val quantity: Int? = null,
    val paymentMethod: String? = null,
    val cardCompany: String? = null,
    val vendor: String? = null,
    val note: String? = null,
)

data class ExpenseResponse(
    val id: UUID,
    val date: LocalDate,
    val itemName: String,
    val category: String,
    val unitPrice: Int,
    val quantity: Int,
    val totalAmount: Int,
    val paymentMethod: String,
    val cardCompany: String?,
    val vendor: String?,
    val note: String?,
    val recurringId: UUID?,
    val isRecurringModified: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: Expense): ExpenseResponse =
            ExpenseResponse(
                id = requireNotNull(e.id),
                date = e.date,
                itemName = e.itemName,
                category = e.category,
                unitPrice = e.unitPrice,
                quantity = e.quantity,
                totalAmount = e.totalAmount,
                paymentMethod = e.paymentMethod,
                cardCompany = e.cardCompany,
                vendor = e.vendor,
                note = e.note,
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
    val notes: List<String>,
)
