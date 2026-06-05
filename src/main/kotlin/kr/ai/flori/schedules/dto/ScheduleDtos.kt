package kr.ai.flori.schedules.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kr.ai.flori.schedules.entity.Schedule
import java.time.Instant
import java.time.LocalDate

data class ScheduleCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String?,
    @field:NotNull(message = "시작일은 필수입니다")
    val startDate: LocalDate?,
    @field:NotNull(message = "종료일은 필수입니다")
    val endDate: LocalDate?,
    val color: String? = null,
    val memo: String? = null,
)

data class ScheduleUpdateRequest(
    val title: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val color: String? = null,
    val memo: String? = null,
)

data class ScheduleResponse(
    val id: Long,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val color: String,
    val memo: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: Schedule): ScheduleResponse =
            ScheduleResponse(
                id = requireNotNull(e.id),
                title = e.title,
                startDate = e.startDate,
                endDate = e.endDate,
                color = e.color,
                memo = e.memo,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
            )
    }
}
