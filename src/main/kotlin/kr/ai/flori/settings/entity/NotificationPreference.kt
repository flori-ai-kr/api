package kr.ai.flori.settings.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 푸시 타입별 수신 설정. (user_id, type) 당 1행. 행이 없으면 기본 켜짐(true)으로 간주한다.
 * 멀티테넌시: user_id 격리.
 */
@Entity
@Table(name = "notification_preferences")
class NotificationPreference(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "type", nullable = false)
    var type: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true
}
