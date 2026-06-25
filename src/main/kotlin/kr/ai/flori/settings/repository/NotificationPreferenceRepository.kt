package kr.ai.flori.settings.repository

import kr.ai.flori.settings.entity.NotificationPreference
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, Long> {
    fun findByUserId(userId: Long): List<NotificationPreference>

    fun findByUserIdAndType(
        userId: Long,
        type: String,
    ): NotificationPreference?
}
