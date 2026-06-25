package kr.ai.flori.support.event

data class InquiryAnsweredEvent(
    val inquiryId: Long,
    val userId: Long,
    val title: String,
)
