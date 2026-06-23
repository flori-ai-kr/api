package kr.ai.flori.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/**
 * 운영 콘솔 푸시 브로드캐스트 1건. 세그먼트(전체/활성/인증/휴면/AI미사용) 대상 발송 정의.
 * 발송 전(draft/scheduled)에만 수정/삭제 가능하며, 발송 시 status=sent로 전이하고 발송 카운트를 기록한다.
 */
@Entity
@Table(name = "broadcasts")
class Broadcast(
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "body", nullable = false)
    var body: String,
    @Column(name = "created_by", nullable = false)
    var createdBy: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "deep_link")
    var deepLink: String? = null

    @Column(name = "segment", nullable = false)
    var segment: String = "all"

    @Column(name = "status", nullable = false)
    var status: String = "draft"

    @Column(name = "scheduled_at")
    var scheduledAt: Instant? = null

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    @Column(name = "target_count", nullable = false)
    var targetCount: Int = 0

    @Column(name = "sent_count", nullable = false)
    var sentCount: Int = 0

    @Column(name = "failed_count", nullable = false)
    var failedCount: Int = 0
}
