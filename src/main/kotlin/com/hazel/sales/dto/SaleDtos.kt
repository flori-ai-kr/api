package com.hazel.sales.dto

import com.hazel.sales.entity.Sale
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 매출 생성. fee/expected_deposit/expected_deposit_date/deposit_status/is_unpaid는
 * 서버가 계산하므로 요청에 포함하지 않는다(앱은 표시만).
 */
data class SaleCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    @field:NotBlank(message = "상품 카테고리는 필수입니다")
    val productCategory: String?,
    @field:NotNull(message = "금액은 필수입니다")
    @field:Min(value = 0, message = "금액은 0 이상이어야 합니다")
    val amount: Int?,
    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String?,
    val cardCompany: String? = null,
    val reservationChannel: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerId: UUID? = null,
    val note: String? = null,
)

/** 매출 부분 수정. 제공된(non-null) 필드만 반영. */
data class SaleUpdateRequest(
    val date: LocalDate? = null,
    val productCategory: String? = null,
    @field:Min(value = 0, message = "금액은 0 이상이어야 합니다")
    val amount: Int? = null,
    val paymentMethod: String? = null,
    val cardCompany: String? = null,
    val reservationChannel: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerId: UUID? = null,
    val note: String? = null,
    val hasReview: Boolean? = null,
)

data class CompleteUnpaidRequest(
    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String?,
)

data class SaleResponse(
    val id: UUID,
    val date: LocalDate,
    val productName: String,
    val productCategory: String?,
    val amount: Int,
    val paymentMethod: String,
    val cardCompany: String?,
    val fee: Int?,
    val expectedDeposit: Int?,
    val expectedDepositDate: LocalDate?,
    val depositStatus: String,
    val depositedAt: Instant?,
    val reservationChannel: String,
    val customerName: String?,
    val customerPhone: String?,
    val customerId: UUID?,
    val note: String?,
    val isUnpaid: Boolean,
    val hasReview: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(sale: Sale): SaleResponse =
            SaleResponse(
                id = requireNotNull(sale.id),
                date = sale.date,
                productName = sale.productName,
                productCategory = sale.productCategory,
                amount = sale.amount,
                paymentMethod = sale.paymentMethod,
                cardCompany = sale.cardCompany,
                fee = sale.fee,
                expectedDeposit = sale.expectedDeposit,
                expectedDepositDate = sale.expectedDepositDate,
                depositStatus = sale.depositStatus,
                depositedAt = sale.depositedAt,
                reservationChannel = sale.reservationChannel,
                customerName = sale.customerName,
                customerPhone = sale.customerPhone,
                customerId = sale.customerId,
                note = sale.note,
                isUnpaid = sale.isUnpaid,
                hasReview = sale.hasReview,
                createdAt = sale.createdAt,
                updatedAt = sale.updatedAt,
            )
    }
}

data class SalesPageResponse(
    val sales: List<SaleResponse>,
    val hasMore: Boolean,
)

data class SaleSuggestionsResponse(
    val notes: List<String>,
)
