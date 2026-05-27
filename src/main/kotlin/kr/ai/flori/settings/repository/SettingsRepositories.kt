package kr.ai.flori.settings.repository

import kr.ai.flori.settings.entity.PushSubscription
import kr.ai.flori.settings.entity.UserPreferences
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPreferencesRepository : JpaRepository<UserPreferences, UUID>

interface PushSubscriptionRepository : JpaRepository<PushSubscription, UUID> {
    fun findByUserIdAndEndpoint(
        userId: UUID,
        endpoint: String,
    ): PushSubscription?

    fun existsByUserIdAndIsActiveTrue(userId: UUID): Boolean
}
