package kr.ai.flori.customers.dto

import jakarta.validation.constraints.NotBlank
import kr.ai.flori.customers.entity.Customer
import java.time.Instant
import java.time.LocalDate

data class CustomerCreateRequest(
    @field:NotBlank(message = "이름은 필수입니다")
    val name: String?,
    @field:NotBlank(message = "전화번호는 필수입니다")
    val phone: String?,
    val grade: String? = null,
    val gender: String? = null,
    val note: String? = null,
)

data class CustomerUpdateRequest(
    val name: String? = null,
    val phone: String? = null,
    val grade: String? = null,
    val gender: String? = null,
    val note: String? = null,
)

data class UpdateGradeRequest(
    @field:NotBlank(message = "등급은 필수입니다")
    val grade: String?,
)

data class FindOrCreateCustomerRequest(
    @field:NotBlank(message = "이름은 필수입니다")
    val name: String?,
    @field:NotBlank(message = "전화번호는 필수입니다")
    val phone: String?,
)

/** 구매 통계는 sales에서 실시간 집계한 값. */
data class CustomerResponse(
    val id: Long,
    val name: String,
    val phone: String,
    val grade: String,
    val gender: String?,
    val note: String?,
    val totalPurchaseCount: Int,
    val totalPurchaseAmount: Long,
    val firstPurchaseDate: LocalDate?,
    val lastPurchaseDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            c: Customer,
            stats: CustomerStats,
        ): CustomerResponse =
            CustomerResponse(
                id = requireNotNull(c.id),
                name = c.name,
                phone = c.phone,
                grade = c.grade,
                gender = c.gender,
                note = c.note,
                totalPurchaseCount = stats.count,
                totalPurchaseAmount = stats.total,
                firstPurchaseDate = stats.firstDate,
                lastPurchaseDate = stats.lastDate,
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
    val grade: String,
)
