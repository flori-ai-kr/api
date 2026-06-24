package kr.ai.flori.admin.entity

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
 * 알림 발송 이력 1건(append-only). 모든 푸시 발송(브로드캐스트/예약 리마인더/공지)을 발송 직후 기록한다.
 * 기존 notification_log(멱등성 dedup)와 별개 — 이건 운영 콘솔 발송 로그 화면용.
 */
@Entity
@Table(name = "notification_send_logs")
class NotificationSendLog(
    @Column(name = "source", nullable = false)
    var source: String,
    @Column(name = "type", nullable = false)
    var type: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "segment")
    var segment: String? = null

    @Column(name = "target_user_id")
    var targetUserId: Long? = null

    @Column(name = "title")
    var title: String? = null

    @Column(name = "body")
    var body: String? = null

    @Column(name = "status", nullable = false)
    var status: String = "sent"

    @Column(name = "sent_count", nullable = false)
    var sentCount: Int = 0

    @Column(name = "failed_count", nullable = false)
    var failedCount: Int = 0

    @Column(name = "error_message")
    var errorMessage: String? = null

    @Column(name = "broadcast_id")
    var broadcastId: Long? = null

    @Column(name = "actor_user_id")
    var actorUserId: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    var metadata: JsonNode = JsonNodeFactory.instance.objectNode()
}
