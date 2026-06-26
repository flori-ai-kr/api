package kr.ai.flori.storage.event

data class StorageIncreaseRequestedEvent(
    val requestId: Long,
    val userId: Long,
    val reason: String?,
    val nickname: String?,
    val storeName: String?,
    val usedBytes: Long,
    val quotaBytes: Long,
)
