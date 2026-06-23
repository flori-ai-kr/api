package kr.ai.flori.admin.dto

import java.time.Instant

/** 알림 발송 이력 1건. */
data class NotificationSendLogResponse(
    val id: Long,
    val source: String,
    val type: String,
    val segment: String?,
    val targetUserId: Long?,
    val title: String?,
    val body: String?,
    val status: String,
    val sentCount: Int,
    val failedCount: Int,
    val errorMessage: String?,
    val broadcastId: Long?,
    val actorUserId: Long?,
    val createdAt: Instant?,
)
