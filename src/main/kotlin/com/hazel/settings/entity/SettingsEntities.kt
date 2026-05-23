package com.hazel.settings.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 카드사 설정(수수료율/입금 영업일). (name, user_id) 복합 unique. 삭제는 is_active=false 소프트 삭제.
 */
@Entity
@Table(name = "card_company_settings")
class CardCompanySetting(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "name", nullable = false)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "fee_rate")
    var feeRate: BigDecimal = BigDecimal("2.0")

    @Column(name = "deposit_days")
    var depositDays: Int = DEFAULT_DEPOSIT_DAYS

    @Column(name = "is_active")
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    companion object {
        const val DEFAULT_DEPOSIT_DAYS = 3
    }
}

/**
 * 유저 설정(하단바 커스터마이즈). PK는 user_id. bottom_nav_items는 jsonb 문자열 배열.
 */
@Entity
@Table(name = "user_preferences")
class UserPreferences(
    @Id
    @Column(name = "user_id")
    var userId: UUID,
) {
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bottom_nav_items", columnDefinition = "jsonb")
    var bottomNavItems: List<String> = emptyList()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}

/**
 * 푸시 구독. endpoint(FCM 토큰)는 unique. 해지는 is_active=false.
 */
@Entity
@Table(name = "push_subscriptions")
class PushSubscription(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "endpoint", nullable = false)
    var endpoint: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "p256dh")
    var p256dh: String? = null

    @Column(name = "auth")
    var auth: String? = null

    @Column(name = "user_agent")
    var userAgent: String? = null

    @Column(name = "is_active")
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
