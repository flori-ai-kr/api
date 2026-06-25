package kr.ai.flori.storage.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 스토리지 증설 요청 이력. 점주가 90%+ 도달 시 사유와 함께 제출 → Discord 알림 → 관리자가 quota 상향 시 RESOLVED.
 * 멀티테넌시: 본인 조회는 user_id 격리, 운영 조회(search)는 @RequiresAdmin 하위 cross-tenant.
 */
@Entity
@Table(name = "storage_increase_requests")
class StorageIncreaseRequest(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "reason", columnDefinition = "text")
    var reason: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "status", nullable = false)
    var status: String = STATUS_PENDING
        protected set

    @Column(name = "resolved_bytes")
    var resolvedBytes: Long? = null
        protected set

    /** 관리자가 quota를 상향했을 때 호출 — 해결 처리. */
    fun resolve(newQuotaBytes: Long) {
        status = STATUS_RESOLVED
        resolvedBytes = newQuotaBytes
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_RESOLVED = "RESOLVED"
    }
}
