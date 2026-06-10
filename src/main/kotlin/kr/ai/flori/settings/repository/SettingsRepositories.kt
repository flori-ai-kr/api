package kr.ai.flori.settings.repository

import kr.ai.flori.settings.entity.PushSubscription
import kr.ai.flori.settings.entity.UserPreference
import kr.ai.flori.settings.entity.UserPreferenceId
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceRepository : JpaRepository<UserPreference, UserPreferenceId> {
    fun findByUserIdAndKey(
        userId: Long,
        key: String,
    ): UserPreference?
}

interface PushSubscriptionRepository : JpaRepository<PushSubscription, Long> {
    fun findByUserIdAndEndpoint(
        userId: Long,
        endpoint: String,
    ): PushSubscription?

    fun existsByUserIdAndIsActiveTrue(userId: Long): Boolean
}
