package kr.ai.flori.settings.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 유저 설정(하단바 커스터마이즈). PK는 user_id. bottom_nav_items는 jsonb 문자열 배열.
 */
@Entity
@Table(name = "user_preferences")
class UserPreferences(
    @Id
    @Column(name = "user_id")
    var userId: Long,
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
    var userId: Long,
    @Column(name = "endpoint", nullable = false)
    var endpoint: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "p256dh")
    var p256dh: String? = null

    @Column(name = "auth")
    var auth: String? = null

    @Column(name = "user_agent")
    var userAgent: String? = null

    @Column(name = "is_active")
    var isActive: Boolean = true
}
