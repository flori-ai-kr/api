package kr.ai.flori.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/** 쿠폰 정의(콘솔 발행). days = 무료 일수. status ∈ ACTIVE/DISABLED. */
@Entity
@Table(name = "coupon")
class Coupon(
    @Column(name = "code", nullable = false)
    var code: String,
    @Column(name = "days", nullable = false)
    var days: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "grant_type", nullable = false)
    var grantType: String = "FREE_DAYS"

    @Column(name = "valid_from")
    var validFrom: Instant? = null

    @Column(name = "valid_until")
    var validUntil: Instant? = null

    @Column(name = "max_redemptions")
    var maxRedemptions: Int? = null

    @Column(name = "per_user_limit", nullable = false)
    var perUserLimit: Int = 1

    @Column(name = "redeemed_count", nullable = false)
    var redeemedCount: Int = 0

    @Column(name = "status", nullable = false)
    var status: String = "ACTIVE"

    @Column(name = "source", nullable = false)
    var source: String = "PROMO"

    @Column(name = "memo")
    var memo: String? = null

    @Column(name = "created_by")
    var createdBy: Long? = null
}
