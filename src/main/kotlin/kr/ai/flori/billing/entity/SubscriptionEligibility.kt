package kr.ai.flori.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/** 어뷰징 방어 신원 원장. identity_hash = SHA-256("biz:"+businessNumber)(사업자등록번호 기준). 탈퇴해도 유지. */
@Entity
@Table(name = "subscription_eligibility")
class SubscriptionEligibility(
    @Column(name = "identity_hash", nullable = false)
    var identityHash: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "trial_used_at")
    var trialUsedAt: Instant? = null
}
