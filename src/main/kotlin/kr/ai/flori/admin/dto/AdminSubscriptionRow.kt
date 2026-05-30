package kr.ai.flori.admin.dto

import java.time.Instant

/** 운영 콘솔 구독 1행. */
data class AdminSubscriptionRow(
    val userId: Long,
    val status: String,
    val store: String,
    val productId: String,
    val entitlement: String,
    val currentPeriodEnd: Instant?,
)
