package kr.ai.flori.ai.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/**
 * AI 대화 세션. web이 session_token으로 멀티턴을 이어간다(ai-server는 stateless).
 * 멀티테넌시: user_id 격리. 삭제는 soft delete(deleted_at).
 */
@Entity
@Table(name = "ai_chat_session")
class AiChatSession(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "session_token", nullable = false, unique = true)
    var sessionToken: String,
    @Column(name = "feature", nullable = false)
    var feature: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "title")
    var title: String? = null

    @Column(name = "first_message_at")
    var firstMessageAt: Instant? = null

    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
}
