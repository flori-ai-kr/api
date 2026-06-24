package kr.ai.flori.admin.dto

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** 운영자 감사 로그 1건. */
data class AdminAuditLogResponse(
    val id: Long,
    val actorUserId: Long,
    val actorEmail: String?,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val summary: String?,
    val metadata: JsonNode,
    val createdAt: Instant?,
)
