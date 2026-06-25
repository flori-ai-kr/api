package kr.ai.flori.support.event

data class InquiryCreatedEvent(
    val inquiryId: Long,
    val userId: Long,
    val category: String,
    val title: String,
    val nickname: String?,
    val storeName: String?,
)
