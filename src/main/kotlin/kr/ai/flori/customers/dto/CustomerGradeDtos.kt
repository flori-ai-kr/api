package kr.ai.flori.customers.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import kr.ai.flori.customers.entity.CustomerGrade

data class CustomerGradeCreateRequest(
    @field:NotBlank @field:Size(max = 50) val name: String,
    @field:PositiveOrZero val threshold: Int? = null, // null = 수동 전용
)

data class CustomerGradeUpdateRequest(
    @field:Size(max = 50) val name: String? = null,
    val threshold: Int? = null,
    @field:PositiveOrZero val sortOrder: Int? = null,
    val clearThreshold: Boolean = false, // threshold 를 명시적으로 NULL(수동전용)로
)

data class CustomerGradeResponse(
    val id: Long,
    val name: String,
    val threshold: Int?,
    val sortOrder: Int,
) {
    companion object {
        fun from(g: CustomerGrade) =
            CustomerGradeResponse(
                id = requireNotNull(g.id),
                name = g.name,
                threshold = g.threshold,
                sortOrder = g.sortOrder,
            )
    }
}
