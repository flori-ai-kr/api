package kr.ai.flori.storage.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant

data class AdminStorageRequestResponse(
    val id: Long,
    val userId: Long,
    val nickname: String?,
    val storeName: String?,
    val reason: String?,
    val status: String,
    val rejectReason: String?,
    val resolvedBytes: Long?,
    val usedBytes: Long,
    val quotaBytes: Long,
    val createdAt: Instant,
)

data class ApproveRequest(
    @field:Positive(message = "한도는 양수여야 합니다")
    val quotaBytes: Long,
)

data class RejectRequest(
    @field:NotBlank(message = "거절 사유를 입력해 주세요")
    @field:Size(max = 1000, message = "거절 사유가 너무 깁니다")
    val reason: String,
)
