package com.hazel.sales.dto

import com.hazel.sales.entity.Sale
import io.swagger.v3.oas.annotations.media.Schema
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
@Schema(description = "매출 생성 요청")
data class SaleCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    @field:Schema(description = "매출 발생일", example = "2026-05-25")
    val date: LocalDate?,
    @field:NotBlank(message = "상품 카테고리는 필수입니다")
    @field:Schema(description = "상품 카테고리(매출설정 value)", example = "꽃다발")
    val productCategory: String?,
    @field:NotNull(message = "금액은 필수입니다")
    @field:Min(value = 0, message = "금액은 0 이상이어야 합니다")
    @field:Schema(description = "결제 금액(원)", example = "50000")
    val amount: Int?,
    @field:NotBlank(message = "결제방식은 필수입니다")
    @field:Schema(description = "결제방식. 'unpaid'면 미수로 생성된다.", example = "card")
    val paymentMethod: String?,
    @field:Schema(description = "카드사(card 결제 시 수수료/입금예정일 계산 기준)", example = "신한")
    val cardCompany: String? = null,
    @field:Schema(description = "예약 채널", example = "instagram")
    val reservationChannel: String? = null,
    @field:Schema(description = "고객명(비회원 입력 가능)", example = "김하늘")
    val customerName: String? = null,
    @field:Schema(description = "고객 전화번호")
    val customerPhone: String? = null,
    @field:Schema(description = "연결할 고객 ID(본인 소유 검증)")
    val customerId: UUID? = null,
    @field:Schema(description = "비고")
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

@Schema(description = "매출 응답. fee/expectedDeposit/expectedDepositDate/depositStatus/isUnpaid는 서버가 계산하는 SSOT 값이다(앱은 표시만).")
data class SaleResponse(
    val id: UUID,
    val date: LocalDate,
    val productName: String,
    val productCategory: String?,
    val amount: Int,
    val paymentMethod: String,
    val cardCompany: String?,
    @field:Schema(description = "카드 수수료(서버 계산: amount * fee_rate/100)", example = "1100")
    val fee: Int?,
    @field:Schema(description = "예상 입금액(서버 계산: amount - fee)", example = "48900")
    val expectedDeposit: Int?,
    @field:Schema(description = "입금 예정일(서버 계산: 영업일 N일)", example = "2026-05-28")
    val expectedDepositDate: LocalDate?,
    @field:Schema(description = "입금 상태", example = "pending", allowableValues = ["not_applicable", "pending", "completed"])
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
