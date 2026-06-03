package kr.ai.flori.ai.entity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * OCR→예약 쓰기 제안(human-in-loop). ai-server가 추출한 초안을 게이트웨이가 보관·만료·감사한다.
 * 확인(/ai/confirm) 시 게이트웨이가 직접 예약을 생성하고 status=confirmed + result_id를 기록한다.
 */
@Entity
@Table(name = "ai_write_proposal")
class AiWriteProposal(
    @Column(name = "proposal_id", nullable = false, unique = true)
    var proposalId: String,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "action", nullable = false)
    var action: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb", nullable = false)
    var payloadJson: JsonNode = JsonNodeFactory.instance.objectNode()

    @Column(name = "source", nullable = false)
    var source: String = "ocr_image"

    @Column(name = "status", nullable = false)
    var status: String = "pending"

    @Column(name = "result_id")
    var resultId: Long? = null

    @Column(name = "model")
    var model: String? = null

    @Column(name = "input_tokens")
    var inputTokens: Int? = null

    @Column(name = "output_tokens")
    var outputTokens: Int? = null

    @Column(name = "latency_ms")
    var latencyMs: Int? = null

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null
}
