package kr.ai.flori.storage.dto

import jakarta.validation.constraints.Positive
import java.time.Instant

data class AdminStorageRequestResponse(
    val id: Long,
    val userId: Long,
    val nickname: String?,
    val storeName: String?,
    val reason: String?,
    val status: String,
    val usedBytes: Long,
    val quotaBytes: Long,
    val createdAt: Instant,
)

data class QuotaUpdateRequest(
    @field:Positive(message = "한도는 양수여야 합니다")
    val quotaBytes: Long,
)
