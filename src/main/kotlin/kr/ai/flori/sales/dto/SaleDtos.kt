package kr.ai.flori.sales.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.sales.entity.Sale
import java.time.Instant
import java.time.LocalDate

/**
 * 매출 생성. 미수는 isUnpaid=true(모달 체크박스)로 표현하며, 이때 paymentMethodId는 무시되고 NULL로 저장된다.
 * isUnpaid=false면 paymentMethodId가 필수다(서버 검증).
 */
data class SaleCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    @field:NotNull(message = "상품 카테고리는 필수입니다")
    val categoryId: Long?,
    @field:NotNull(message = "금액은 필수입니다")
    @field:Min(value = 0, message = "금액은 0 이상이어야 합니다")
    val amount: Int?,
    val paymentMethodId: Long? = null,
    val isUnpaid: Boolean = false,
    val channelId: Long? = null,
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
    val categoryId: Long? = null,
    @field:Min(value = 0, message = "금액은 0 이상이어야 합니다")
    val amount: Int? = null,
    val paymentMethodId: Long? = null,
    val channelId: Long? = null,
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
    @field:NotNull(message = "결제방식은 필수입니다")
    val paymentMethodId: Long?,
)

data class SaleResponse(
    val id: Long,
    val date: LocalDate,
    val categoryId: Long?,
    val categoryLabel: String?,
    val amount: Int,
    val paymentMethodId: Long?,
    val paymentMethodLabel: String?,
    val channelId: Long?,
    val channelLabel: String?,
    val customerName: String?,
    val customerPhone: String?,
    val customerId: Long?,
    val memo: String?,
    val isUnpaid: Boolean,
    val hasReview: Boolean,
    val photos: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            sale: Sale,
            categoryLabel: String?,
            paymentMethodLabel: String?,
            channelLabel: String?,
            photos: List<String> = emptyList(),
        ): SaleResponse =
            SaleResponse(
                id = requireNotNull(sale.id),
                date = sale.date,
                categoryId = sale.categoryId,
                categoryLabel = categoryLabel,
                amount = sale.amount,
                paymentMethodId = sale.paymentMethodId,
                paymentMethodLabel = paymentMethodLabel,
                channelId = sale.channelId,
                channelLabel = channelLabel,
                customerName = sale.customerName,
                customerPhone = sale.customerPhone,
                customerId = sale.customerId,
                memo = sale.memo,
                isUnpaid = sale.isUnpaid,
                hasReview = sale.hasReview,
                photos = photos,
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
