package kr.ai.flori.calendar.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kr.ai.flori.calendar.entity.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CalendarEventCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String?,
    @field:NotNull(message = "시작일은 필수입니다")
    val startDate: LocalDate?,
    @field:NotNull(message = "종료일은 필수입니다")
    val endDate: LocalDate?,
    val color: String? = null,
    val description: String? = null,
)

data class CalendarEventUpdateRequest(
    val title: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val color: String? = null,
    val description: String? = null,
)

data class CalendarEventResponse(
    val id: UUID,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val color: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: CalendarEvent): CalendarEventResponse =
            CalendarEventResponse(
                id = requireNotNull(e.id),
                title = e.title,
                startDate = e.startDate,
                endDate = e.endDate,
                color = e.color,
                description = e.description,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
            )
    }
}
