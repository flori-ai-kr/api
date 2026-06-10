package kr.ai.flori.verification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import java.time.Instant

/**
 * 사업자 인증 신청 1건. 재신청 시 새 행으로 누적된다(append 지향).
 * 멀티테넌시: 모든 조회/변경은 user_id로 격리(서비스에서 강제).
 * 승인/거절은 현재 수동(운영자 SQL)으로 status/reject_reason/reviewed_at를 갱신하며,
 * 향후 관리자 API에서는 [approve]/[reject] 도메인 메서드를 통해 상태를 전이한다.
 */
@Entity
@Table(name = "business_verifications")
class BusinessVerification(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "business_number", nullable = false)
    var businessNumber: String,
    @Column(name = "business_name", nullable = false)
    var businessName: String,
    @Column(name = "representative_name", nullable = false)
    var representativeName: String,
    @Column(name = "business_license_url", nullable = false)
    var businessLicenseUrl: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: BusinessVerificationStatuses = BusinessVerificationStatuses.PENDING
        protected set

    @Column(name = "reject_reason")
    var rejectReason: String? = null
        protected set

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null
        protected set

    /** 승인 처리(상태 전이). */
    fun approve() {
        status = BusinessVerificationStatuses.APPROVED
        rejectReason = null
        reviewedAt = Instant.now()
    }

    /** 거절 처리(사유 기록). */
    fun reject(reason: String) {
        status = BusinessVerificationStatuses.REJECTED
        rejectReason = reason
        reviewedAt = Instant.now()
    }
}
