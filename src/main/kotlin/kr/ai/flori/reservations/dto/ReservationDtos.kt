package kr.ai.flori.reservations.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kr.ai.flori.reservations.entity.Reservation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class ReservationCreateRequest(
    @field:NotNull(message = "날짜는 필수입니다")
    val date: LocalDate?,
    val time: LocalTime? = null,
    @field:NotBlank(message = "고객명은 필수입니다")
    val customerName: String?,
    val customerPhone: String? = null,
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String?,
    val description: String? = null,
    val amount: Int = 0,
    val status: String? = null,
    val reminderAt: Instant? = null,
)

data class ReservationUpdateRequest(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val title: String? = null,
    val description: String? = null,
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
    val description: String?,
    val status: String,
    val saleId: Long?,
    val amount: Int,
    val reminderAt: Instant?,
    val reminderSent: Boolean,
    val pickupCompleted: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(r: Reservation): ReservationResponse =
            ReservationResponse(
                id = requireNotNull(r.id),
                date = r.date,
                time = r.time,
                customerName = r.customerName,
                customerPhone = r.customerPhone,
                title = r.title,
                description = r.description,
                status = r.status,
                saleId = r.saleId,
                amount = r.amount,
                reminderAt = r.reminderAt,
                reminderSent = r.reminderSent,
                pickupCompleted = r.pickupCompleted,
                createdAt = r.createdAt,
                updatedAt = r.updatedAt,
            )
    }
}

data class ReservationSuggestionsResponse(
    val titles: List<String>,
    val descriptions: List<String>,
)
