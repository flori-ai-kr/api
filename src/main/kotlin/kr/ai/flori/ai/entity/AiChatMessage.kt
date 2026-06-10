package kr.ai.flori.ai.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * AI 대화 메시지(턴). 토큰/모델/지연을 이 행에 직접 기록(별도 usage 테이블 없음).
 * session_id는 간접참조(ai_chat_session.id, FK 없음).
 */
@Entity
@Table(name = "ai_chat_message")
class AiChatMessage(
    @Column(name = "session_id", nullable = false)
    var sessionId: Long,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "role", nullable = false)
    var role: String,
    @Column(name = "content", nullable = false)
    var content: String = "",
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "jsonb")
    var contentJson: JsonNode? = null

    @Column(name = "model")
    var model: String? = null

    @Column(name = "input_tokens")
    var inputTokens: Int? = null

    @Column(name = "output_tokens")
    var outputTokens: Int? = null

    @Column(name = "latency_ms")
    var latencyMs: Int? = null
}
