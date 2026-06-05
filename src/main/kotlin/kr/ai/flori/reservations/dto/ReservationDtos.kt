package kr.ai.flori.reservations.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.reservations.entity.Reservation
import kr.ai.flori.sales.entity.Sale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class ReservationCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    val time: LocalTime? = null,
    @field:NotBlank(message = "고객명은 필수입니다")
    @field:Size(max = FieldLimits.NAME, message = "고객명이 너무 깁니다")
    val customerName: String?,
    @field:Size(max = FieldLimits.PHONE, message = "전화번호가 너무 깁니다")
    val customerPhone: String? = null,
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = FieldLimits.TITLE, message = "제목이 너무 깁니다")
    val title: String?,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
    val amount: Int = 0,
    val status: String? = null,
    val reminderAt: Instant? = null,
)

data class ReservationUpdateRequest(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    @field:Size(max = FieldLimits.NAME, message = "고객명이 너무 깁니다")
    val customerName: String? = null,
    @field:Size(max = FieldLimits.PHONE, message = "전화번호가 너무 깁니다")
    val customerPhone: String? = null,
    @field:Size(max = FieldLimits.TITLE, message = "제목이 너무 깁니다")
    val title: String? = null,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
    val amount: Int? = null,
    val status: String? = null,
    val saleId: Long? = null,
    val reminderAt: Instant? = null,
    val pickupCompleted: Boolean? = null,
)

data class AddPickupRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    val time: LocalTime? = null,
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = FieldLimits.TITLE, message = "제목이 너무 깁니다")
    val title: String?,
    val amount: Int = 0,
    val reminderAt: Instant? = null,
)

data class PickupCompleteRequest(
    @field:NotNull(message = "완료 여부는 필수입니다")
    val completed: Boolean?,
)

data class ReservationResponse(
    val id: Long,
    val date: LocalDate,
    val time: LocalTime?,
    val customerName: String,
    val customerPhone: String?,
    val title: String,
    val memo: String?,
    val status: String,
    val saleId: Long?,
    val amount: Int,
    val reminderAt: Instant?,
    val reminderSent: Boolean,
    val pickupCompleted: Boolean,
    // 연결된 매출(sale)·고객 조인 enrichment — 캘린더 카드 표시용. 매출 미연결 시 null.
    val saleDate: LocalDate?,
    val productCategory: String?,
    val customerId: Long?,
    val purchaseCount: Int?,
    val saleIsUnpaid: Boolean?,
    val salePaymentMethod: String?,
    val saleReservationChannel: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            r: Reservation,
            sale: Sale? = null,
            purchaseCount: Int? = null,
            categoryLabel: String? = null,
            channelLabel: String? = null,
            paymentMethodLabel: String? = null,
        ): ReservationResponse =
            ReservationResponse(
                id = requireNotNull(r.id),
                date = r.date,
                time = r.time,
                customerName = r.customerName,
                customerPhone = r.customerPhone,
                title = r.title,
                memo = r.memo,
                status = r.status,
                saleId = r.saleId,
                amount = r.amount,
                reminderAt = r.reminderAt,
                reminderSent = r.reminderSent,
                pickupCompleted = r.pickupCompleted,
                saleDate = sale?.date,
                // 연결된 매출 카테고리/채널은 label_settings.id → 라벨로 해석해 표시한다.
                productCategory = categoryLabel,
                customerId = sale?.customerId,
                purchaseCount = purchaseCount,
                saleIsUnpaid = sale?.isUnpaid,
                // 연결 매출 결제수단은 label_settings.id → 라벨로 해석해 표시한다.
                salePaymentMethod = paymentMethodLabel,
                saleReservationChannel = channelLabel,
                createdAt = r.createdAt,
                updatedAt = r.updatedAt,
            )
    }
}

data class ReservationSuggestionsResponse(
    val titles: List<String>,
    val memos: List<String>,
)
