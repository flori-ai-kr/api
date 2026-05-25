package kr.ai.flori.subscriptions.entity

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
import java.util.UUID

/**
 * 웹훅 이벤트 이력(append-only 감사 로그). 상태 전이 디버깅·재처리 추적용.
 * raw_event 는 원본 페이로드(JSONB) 그대로 보관한다.
 */
@Entity
@Table(name = "subscription_events")
class SubscriptionEvent(
    @Column(name = "user_id")
    var userId: UUID?,
    @Column(name = "event_type", nullable = false)
    var eventType: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "event_id")
    var eventId: String? = null

    @Column(name = "store")
    var store: String? = null

    @Column(name = "product_id")
    var productId: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_event")
    var rawEvent: String? = null

    @Column(name = "occurred_at")
    var occurredAt: Instant? = null
}
