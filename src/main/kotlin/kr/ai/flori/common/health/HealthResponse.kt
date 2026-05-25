package kr.ai.flori.common.health

import java.time.OffsetDateTime

/**
 * 헬스체크 응답 DTO. 원시 Map 대신 명시적 DTO를 노출해 응답 계약을 고정한다.
 */
data class HealthResponse(
    val status: String,
    val service: String,
    val time: OffsetDateTime,
)
