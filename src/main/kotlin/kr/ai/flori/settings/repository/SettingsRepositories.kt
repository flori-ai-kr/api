package kr.ai.flori.settings.repository

import kr.ai.flori.settings.entity.PushSubscription
import kr.ai.flori.settings.entity.UserPreferences
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferencesRepository : JpaRepository<UserPreferences, Long>

interface PushSubscriptionRepository : JpaRepository<PushSubscription, Long> {
    fun findByUserIdAndEndpoint(
        userId: Long,
        endpoint: String,
    ): PushSubscription?

    fun existsByUserIdAndIsActiveTrue(userId: Long): Boolean
}
