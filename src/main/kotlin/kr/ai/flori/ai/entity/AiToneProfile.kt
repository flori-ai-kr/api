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

/**
 * 사장 말투 프로필(유저당 1행, upsert). 블로그 샘플 1~3개를 매 생성 시 few-shot으로 ai-server에 주입한다.
 * 멀티테넌시: user_id UNIQUE 격리. samples_json은 ["샘플1", ...] 문자열 배열(최대 3).
 */
@Entity
@Table(name = "ai_tone_profile")
class AiToneProfile(
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "samples_json", columnDefinition = "jsonb", nullable = false)
    var samplesJson: JsonNode = JsonNodeFactory.instance.arrayNode()
}
