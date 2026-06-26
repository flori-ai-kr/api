package kr.ai.flori.storage.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 스토리지 증설 요청. PENDING → APPROVED | REJECTED 라이프사이클.
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

    @Column(name = "reject_reason")
    var rejectReason: String? = null
        protected set

    fun approve(newQuotaBytes: Long) {
        check(status == STATUS_PENDING) { "PENDING 상태에서만 승인 가능" }
        status = STATUS_APPROVED
        resolvedBytes = newQuotaBytes
    }

    fun reject(reason: String) {
        check(status == STATUS_PENDING) { "PENDING 상태에서만 거절 가능" }
        status = STATUS_REJECTED
        rejectReason = reason
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
    }
}
