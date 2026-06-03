package kr.ai.flori.ai.entity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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
 * proactive 제안 노출 로그. 생성/노출된 제안(suggestions_json)과 토큰 사용량을 기록(분석·사용량).
 */
@Entity
@Table(name = "ai_proactive_log")
class AiProactiveLog(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestions_json", columnDefinition = "jsonb", nullable = false)
    var suggestionsJson: JsonNode = JsonNodeFactory.instance.arrayNode()

    @Column(name = "suggestion_count", nullable = false)
    var suggestionCount: Int = 0

    @Column(name = "model")
    var model: String? = null

    @Column(name = "input_tokens")
    var inputTokens: Int? = null

    @Column(name = "output_tokens")
    var outputTokens: Int? = null

    @Column(name = "latency_ms")
    var latencyMs: Int? = null
}
