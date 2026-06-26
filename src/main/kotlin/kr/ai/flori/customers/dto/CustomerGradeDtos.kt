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

/** 임계값 변경 미리보기 요청. clearThreshold=true 면 수동전용 전환(threshold 무시). */
data class GradeThresholdPreviewRequest(
    @field:PositiveOrZero val threshold: Int? = null,
    val clearThreshold: Boolean = false,
)

/** 미리보기 항목: 이 변경으로 등급이 바뀔 고객 1명(현재→변경 후). */
data class GradeChangePreviewItem(
    val customerName: String,
    val fromGrade: String?,
    val toGrade: String,
)

data class GradeRecomputePreviewResponse(
    val total: Int,
    val changes: List<GradeChangePreviewItem>,
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
