package kr.ai.flori.storage.dto

import jakarta.validation.constraints.Size
import kr.ai.flori.storage.entity.StorageIncreaseRequest
import kr.ai.flori.storage.service.StorageUsage
import java.time.Instant

data class StorageUsageResponse(
    val usedBytes: Long,
    val quotaBytes: Long,
    val percent: Int,
    val status: String,
) {
    companion object {
        fun from(u: StorageUsage) = StorageUsageResponse(u.usedBytes, u.quotaBytes, u.percent, u.status)
    }
}

data class StorageIncreaseRequestCreate(
    @field:Size(max = 1000, message = "사유가 너무 깁니다")
    val reason: String? = null,
)

data class StorageRequestResponse(
    val id: Long,
    val status: String,
    val reason: String?,
    val resolvedBytes: Long?,
    val rejectReason: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(r: StorageIncreaseRequest) =
            StorageRequestResponse(r.id!!, r.status, r.reason, r.resolvedBytes, r.rejectReason, r.createdAt)
    }
}
