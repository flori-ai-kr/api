package kr.ai.flori.sales.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.sales.entity.Sale
import java.time.Instant
import java.time.LocalDate

/**
 * 매출 생성. is_unpaid는 결제방식(unpaid)으로 서버가 결정하므로 요청에 포함하지 않는다.
 */
data class SaleCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    @field:NotBlank(message = "상품 카테고리는 필수입니다")
    @field:Size(max = FieldLimits.PRODUCT_CATEGORY, message = "상품 카테고리가 너무 깁니다")
    val productCategory: String?,
    @field:NotNull(message = "금액은 필수입니다")
    @field:Min(value = 0, message = "금액은 0 이상이어야 합니다")
    val amount: Int?,
    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String?,
    val reservationChannel: String? = null,
    @field:Size(max = FieldLimits.NAME, message = "고객명이 너무 깁니다")
    val customerName: String? = null,
    @field:Size(max = FieldLimits.PHONE, message = "전화번호가 너무 깁니다")
    val customerPhone: String? = null,
    val customerId: Long? = null,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
)

/** 매출 부분 수정. 제공된(non-null) 필드만 반영. */
data class SaleUpdateRequest(
    val date: LocalDate? = null,
    @field:Size(max = FieldLimits.PRODUCT_CATEGORY, message = "상품 카테고리가 너무 깁니다")
    val productCategory: String? = null,
    @field:Min(value = 0, message = "금액은 0 이상이어야 합니다")
    val amount: Int? = null,
    val paymentMethod: String? = null,
    val reservationChannel: String? = null,
    @field:Size(max = FieldLimits.NAME, message = "고객명이 너무 깁니다")
    val customerName: String? = null,
    @field:Size(max = FieldLimits.PHONE, message = "전화번호가 너무 깁니다")
    val customerPhone: String? = null,
    val customerId: Long? = null,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
    val hasReview: Boolean? = null,
)

data class CompleteUnpaidRequest(
    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String?,
)

data class SaleResponse(
    val id: Long,
    val date: LocalDate,
    val productCategory: String?,
    val amount: Int,
    val paymentMethod: String,
    val reservationChannel: String,
    val customerName: String?,
    val customerPhone: String?,
    val customerId: Long?,
    val memo: String?,
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
                productCategory = sale.productCategory,
                amount = sale.amount,
                paymentMethod = sale.paymentMethod,
                reservationChannel = sale.reservationChannel,
                customerName = sale.customerName,
                customerPhone = sale.customerPhone,
                customerId = sale.customerId,
                memo = sale.memo,
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
    val memos: List<String>,
)

/**
 * 매출 요약(페이지네이션 무관 전체 합산). GET /sales 와 동일 필터 규약.
 * total/count는 모든 결제수단(미수·kakaopay 포함) 합산, 명세 버킷은 해당 결제수단만.
 */
data class SalesSummaryResponse(
    val total: Long,
    val card: Long,
    val naverpay: Long,
    val transfer: Long,
    val cash: Long,
    val count: Long,
)
