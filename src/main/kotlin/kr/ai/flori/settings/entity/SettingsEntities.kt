package kr.ai.flori.settings.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import org.hibernate.annotations.Type
import java.io.Serializable

/**
 * 유저 설정 — key-value 스토어(확장 가능). PK=(user_id, key), value는 jsonb 원본 문자열.
 * 예: key='bottom_nav', value='["dashboard","sales",...]'. 새 설정은 컬럼 추가 없이 key만 늘린다.
 * created_at/updated_at은 BaseEntity가 자동 관리.
 */
@Entity
@Table(name = "user_preferences")
@IdClass(UserPreferenceId::class)
class UserPreference(
    @Id
    @Column(name = "user_id")
    var userId: Long,
    @Id
    @Column(name = "key")
    var key: String,
) : BaseEntity() {
    @Type(JsonType::class)
    @Column(name = "value", columnDefinition = "jsonb", nullable = false)
    var value: String = "null"
}

/** UserPreference 복합 PK(user_id, key). JPA @IdClass 요구로 Serializable. */
data class UserPreferenceId(
    val userId: Long = 0,
    val key: String = "",
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
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
