package com.hazel.settings.repository

import com.hazel.settings.entity.CardCompanySetting
import com.hazel.settings.entity.PushSubscription
import com.hazel.settings.entity.UserPreferences
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CardCompanyRepository : JpaRepository<CardCompanySetting, UUID> {
    fun findByUserIdAndIsActiveTrueOrderByName(userId: UUID): List<CardCompanySetting>

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): CardCompanySetting?
}

interface UserPreferencesRepository : JpaRepository<UserPreferences, UUID>

interface PushSubscriptionRepository : JpaRepository<PushSubscription, UUID> {
    fun findByUserIdAndEndpoint(
        userId: UUID,
        endpoint: String,
    ): PushSubscription?

    fun existsByUserIdAndIsActiveTrue(userId: UUID): Boolean
}
