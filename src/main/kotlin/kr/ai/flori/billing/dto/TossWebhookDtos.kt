package kr.ai.flori.billing.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TossWebhookEvent(
    val eventType: String? = null,
    val data: TossWebhookData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TossWebhookData(
    val paymentKey: String? = null,
    val orderId: String? = null,
    val status: String? = null,
)
