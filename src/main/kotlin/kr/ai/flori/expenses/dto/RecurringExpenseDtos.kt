package kr.ai.flori.expenses.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kr.ai.flori.expenses.entity.RecurringExpense
import kr.ai.flori.expenses.entity.YearlyDate
import java.time.Instant
import java.time.LocalDate

/** 고정비 생성/수정 공통 입력(전체 교체). */
data class RecurringExpenseRequest(
    @field:NotBlank(message = "물품명은 필수입니다")
    val itemName: String?,
    @field:NotNull(message = "카테고리는 필수입니다")
    val categoryId: Long?,
    @field:NotNull @field:Min(0, message = "단가는 0 이상이어야 합니다")
    val unitPrice: Int?,
    @field:Min(1, message = "수량은 1 이상이어야 합니다")
    val quantity: Int = 1,
    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String?,
    val vendor: String? = null,
    val memo: String? = null,
    @field:NotBlank(message = "반복 주기는 필수입니다")
    val frequency: String?,
    @field:Min(1, message = "간격은 1 이상이어야 합니다")
    val intervalCount: Int = 1,
    val daysOfWeek: List<Int> = emptyList(),
    val daysOfMonth: List<Int> = emptyList(),
    val yearlyDates: List<YearlyDate> = emptyList(),
    @field:NotNull(message = "시작일은 필수입니다")
    val startDate: LocalDate?,
    val endDate: LocalDate? = null,
    val isActive: Boolean = true,
)

data class ToggleActiveRequest(
    @field:NotNull(message = "활성 여부는 필수입니다")
    val isActive: Boolean?,
)

/** this/all 분기에서 인스턴스 기준 수정 입력(부분). */
data class RecurringInstanceUpdateRequest(
    val date: LocalDate? = null,
    val itemName: String? = null,
    val categoryId: Long? = null,
    @field:Min(0, message = "단가는 0 이상이어야 합니다")
    val unitPrice: Int? = null,
    @field:Min(1, message = "수량은 1 이상이어야 합니다")
    val quantity: Int? = null,
    val paymentMethod: String? = null,
    val vendor: String? = null,
    val memo: String? = null,
)

data class RecurringExpenseResponse(
    val id: Long,
    val itemName: String,
    val categoryId: Long?,
    val categoryLabel: String?,
    val unitPrice: Int,
    val quantity: Int,
    val paymentMethod: String,
    val vendor: String?,
    val memo: String?,
    val frequency: String,
    val intervalCount: Int,
    val daysOfWeek: List<Int>,
    val daysOfMonth: List<Int>,
    val yearlyDates: List<YearlyDate>,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            r: RecurringExpense,
            categoryLabel: String?,
        ): RecurringExpenseResponse =
            RecurringExpenseResponse(
                id = requireNotNull(r.id),
                itemName = r.itemName,
                categoryId = r.categoryId,
                categoryLabel = categoryLabel,
                unitPrice = r.unitPrice,
                quantity = r.quantity,
                paymentMethod = r.paymentMethod,
                vendor = r.vendor,
                memo = r.memo,
                frequency = r.frequency,
                intervalCount = r.intervalCount,
                daysOfWeek = r.daysOfWeek,
                daysOfMonth = r.daysOfMonth,
                yearlyDates = r.yearlyDates,
                startDate = r.startDate,
                endDate = r.endDate,
                isActive = r.isActive,
                createdAt = r.createdAt,
                updatedAt = r.updatedAt,
            )
    }
}
