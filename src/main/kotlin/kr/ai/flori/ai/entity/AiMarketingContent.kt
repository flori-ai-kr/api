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
import java.time.Instant

/**
 * AI 마케팅 생성 콘텐츠(블로그 초안 등). 입력(키워드/상황/메모/사진·조립 맥락)과 출력(BlogDraft)을 함께 보관.
 * 토큰/모델/지연을 이 행에 직접 기록(별도 usage 테이블 없음). 삭제는 soft delete(deleted_at).
 * 멀티테넌시: user_id 격리.
 */
@Entity
@Table(name = "ai_marketing_content")
class AiMarketingContent(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "channel", nullable = false)
    var channel: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", columnDefinition = "jsonb", nullable = false)
    var inputJson: JsonNode = JsonNodeFactory.instance.objectNode()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_json", columnDefinition = "jsonb", nullable = false)
    var outputJson: JsonNode = JsonNodeFactory.instance.objectNode()

    @Column(name = "model")
    var model: String? = null

    @Column(name = "input_tokens")
    var inputTokens: Int? = null

    @Column(name = "output_tokens")
    var outputTokens: Int? = null

    @Column(name = "latency_ms")
    var latencyMs: Int? = null

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
}
