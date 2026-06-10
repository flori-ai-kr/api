package kr.ai.flori.customers.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits
import kr.ai.flori.customers.entity.Customer
import java.time.Instant
import java.time.LocalDate

data class CustomerCreateRequest(
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(max = FieldLimits.NAME, message = "이름이 너무 깁니다")
    val name: String?,
    @field:NotBlank(message = "전화번호는 필수입니다")
    @field:Size(max = FieldLimits.PHONE, message = "전화번호가 너무 깁니다")
    val phone: String?,
    val gender: String? = null,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
)

data class CustomerUpdateRequest(
    @field:Size(max = FieldLimits.NAME, message = "이름이 너무 깁니다")
    val name: String? = null,
    @field:Size(max = FieldLimits.PHONE, message = "전화번호가 너무 깁니다")
    val phone: String? = null,
    val gender: String? = null,
    @field:Size(max = FieldLimits.MEMO, message = "메모가 너무 깁니다")
    val memo: String? = null,
)

/** 수동 등급 지정 요청. 지정 시 등급 잠금(자동 재계산 제외). */
data class CustomerGradeAssignRequest(
    @field:NotNull(message = "등급 id는 필수입니다")
    val gradeId: Long?,
)

data class FindOrCreateCustomerRequest(
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(max = FieldLimits.NAME, message = "이름이 너무 깁니다")
    val name: String?,
    @field:NotBlank(message = "전화번호는 필수입니다")
    @field:Size(max = FieldLimits.PHONE, message = "전화번호가 너무 깁니다")
    val phone: String?,
)

/** 고객 대표 사진 썸네일 — url + 사진첩 딥링크용 cardId. */
data class PhotoThumbnail(
    val url: String,
    val cardId: Long,
)

/**
 * 구매 통계는 sales에서 실시간 집계한 값.
 * grade는 gradeId로 해석한 등급명(배지 표기용), gradeId/gradeLocked는 등급 관리 UI용.
 * photoThumbnails/photoCount는 이 고객에 연결된 사진첩 대표 썸네일(최대 6)·총 카운트.
 */
data class CustomerResponse(
    val id: Long,
    val name: String,
    val phone: String,
    val gradeId: Long?,
    val grade: String?,
    val gradeLocked: Boolean,
    val gender: String?,
    val memo: String?,
    val totalPurchaseCount: Int,
    val totalPurchaseAmount: Long,
    val firstPurchaseDate: LocalDate?,
    val lastPurchaseDate: LocalDate?,
    val photoThumbnails: List<PhotoThumbnail>,
    val photoCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            c: Customer,
            stats: CustomerStats,
            gradeName: String?,
            photoThumbnails: List<PhotoThumbnail>,
            photoCount: Int,
        ): CustomerResponse =
            CustomerResponse(
                id = requireNotNull(c.id),
                name = c.name,
                phone = c.phone,
                gradeId = c.gradeId,
                grade = gradeName,
                gradeLocked = c.gradeLocked,
                gender = c.gender,
                memo = c.memo,
                totalPurchaseCount = stats.count,
                totalPurchaseAmount = stats.total,
                firstPurchaseDate = stats.firstDate,
                lastPurchaseDate = stats.lastDate,
                photoThumbnails = photoThumbnails,
                photoCount = photoCount,
                createdAt = c.createdAt,
                updatedAt = c.updatedAt,
            )
    }
}

data class CustomerStats(
    val count: Int,
    val total: Long,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
) {
    companion object {
        val EMPTY = CustomerStats(0, 0, null, null)
    }
}

data class CustomerSearchResult(
    val id: Long,
    val name: String,
    val phone: String,
    val grade: String?,
)
