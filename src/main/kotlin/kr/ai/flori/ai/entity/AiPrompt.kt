package kr.ai.flori.ai.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.math.BigDecimal
import java.time.Instant

/**
 * AI 프롬프트 레지스트리(SPEC-AI-008). 마케팅 생성 프롬프트의 정적 부분을 DB로 관리한다.
 *
 * 본문 3조각(systemMd/rulesMd/outputSpecMd) + 모델/파라미터(model/temperature/maxTokens)만 보관한다.
 * 동적 데이터(키워드·말투샘플·매장맥락)는 게이트웨이가 런타임에 코드로 주입한다(여기 저장 안 함).
 * 채널당 active 1개 불변식(uq_ai_prompt_active_per_channel). 삭제는 soft delete(deletedAt).
 */
@Entity
@Table(name = "ai_prompt")
class AiPrompt(
    @Column(name = "channel", nullable = false)
    var channel: String,
    @Column(name = "version", nullable = false)
    var version: String,
    @Column(name = "system_md", nullable = false, columnDefinition = "text")
    var systemMd: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false

    @Column(name = "rules_md", nullable = false, columnDefinition = "text")
    var rulesMd: String = ""

    @Column(name = "output_spec_md", nullable = false, columnDefinition = "text")
    var outputSpecMd: String = ""

    @Column(name = "model")
    var model: String? = null

    @Column(name = "temperature")
    var temperature: BigDecimal? = null

    @Column(name = "max_tokens")
    var maxTokens: Int? = null

    @Column(name = "notes", columnDefinition = "text")
    var notes: String? = null

    @Column(name = "created_by")
    var createdBy: String? = null

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
}
