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
 * 운영자 감사 로그 1건(append-only). 모든 운영자 액션을 책임추적용으로 기록한다.
 * 수정/삭제 없음. before/after·사유 등 가변 맥락은 metadata(jsonb)에 담는다.
 */
@Entity
@Table(name = "admin_audit_logs")
class AdminAuditLog(
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: Long,
    @Column(name = "action", nullable = false)
    var action: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "actor_email")
    var actorEmail: String? = null

    @Column(name = "target_type")
    var targetType: String? = null

    @Column(name = "target_id")
    var targetId: String? = null

    @Column(name = "summary")
    var summary: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    var metadata: JsonNode = JsonNodeFactory.instance.objectNode()

    @Column(name = "ip")
    var ip: String? = null
}
