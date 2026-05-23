package com.hazel.settings.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.settings.dto.CardCompanyResponse
import com.hazel.settings.dto.PushStatusResponse
import com.hazel.settings.dto.UserPreferencesResponse
import com.hazel.settings.entity.CardCompanySetting
import com.hazel.settings.entity.PushSubscription
import com.hazel.settings.entity.UserPreferences
import com.hazel.settings.repository.CardCompanyRepository
import com.hazel.settings.repository.PushSubscriptionRepository
import com.hazel.settings.repository.UserPreferencesRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 카드사 설정. 모든 쿼리 TenantContext 격리(HARD). 삭제는 소프트(is_active=false).
 */
@Service
class CardCompanyService(
    private val repository: CardCompanyRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<CardCompanyResponse> =
        repository.findByUserIdAndIsActiveTrueOrderByName(TenantContext.currentUserId()).map(::toResponse)

    @Transactional
    fun create(
        name: String,
        feeRate: BigDecimal,
        depositDays: Int,
    ): CardCompanyResponse {
        val entity = CardCompanySetting(TenantContext.currentUserId(), name)
        entity.feeRate = feeRate
        entity.depositDays = depositDays
        return toResponse(
            try {
                repository.saveAndFlush(entity)
            } catch (_: DataIntegrityViolationException) {
                throw AppException(ErrorCode.DUPLICATE, "이미 등록된 카드사입니다")
            },
        )
    }

    @Transactional
    fun update(
        id: UUID,
        feeRate: BigDecimal?,
        depositDays: Int?,
    ): CardCompanyResponse {
        val entity = load(id)
        feeRate?.let { entity.feeRate = it }
        depositDays?.let { entity.depositDays = it }
        entity.updatedAt = Instant.now()
        return toResponse(repository.save(entity))
    }

    @Transactional
    fun delete(id: UUID) {
        val entity = load(id)
        entity.isActive = false
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    private fun load(id: UUID): CardCompanySetting =
        repository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "카드사를 찾을 수 없습니다")

    private fun toResponse(e: CardCompanySetting) = CardCompanyResponse(requireNotNull(e.id), e.name, e.feeRate, e.depositDays, e.isActive)
}

/**
 * 유저 설정(하단바). 없으면 기본값 반환, 변경은 upsert.
 */
@Service
class UserPreferenceService(
    private val repository: UserPreferencesRepository,
) {
    @Transactional(readOnly = true)
    fun get(): UserPreferencesResponse {
        val prefs = repository.findById(TenantContext.currentUserId()).orElse(null)
        return UserPreferencesResponse(prefs?.bottomNavItems ?: DEFAULT_BOTTOM_NAV)
    }

    @Transactional
    fun updateBottomNav(items: List<String>): UserPreferencesResponse {
        val userId = TenantContext.currentUserId()
        val prefs = repository.findById(userId).orElseGet { UserPreferences(userId) }
        prefs.bottomNavItems = items
        prefs.updatedAt = Instant.now()
        return UserPreferencesResponse(repository.save(prefs).bottomNavItems)
    }

    private companion object {
        val DEFAULT_BOTTOM_NAV = listOf("dashboard", "sales", "expenses", "customers", "insights")
    }
}

/**
 * 푸시 구독 등록/해지/상태. endpoint(FCM 토큰) 기준 upsert.
 */
@Service
class PushSubscriptionService(
    private val repository: PushSubscriptionRepository,
) {
    @Transactional
    fun subscribe(
        endpoint: String,
        p256dh: String?,
        auth: String?,
        userAgent: String?,
    ) {
        val userId = TenantContext.currentUserId()
        val subscription = repository.findByEndpoint(endpoint) ?: PushSubscription(userId, endpoint)
        subscription.userId = userId
        subscription.p256dh = p256dh
        subscription.auth = auth
        subscription.userAgent = userAgent
        subscription.isActive = true
        subscription.updatedAt = Instant.now()
        repository.save(subscription)
    }

    @Transactional
    fun unsubscribe(endpoint: String) {
        repository.findByUserIdAndEndpoint(TenantContext.currentUserId(), endpoint)?.let {
            it.isActive = false
            it.updatedAt = Instant.now()
            repository.save(it)
        }
    }

    @Transactional(readOnly = true)
    fun status(): PushStatusResponse = PushStatusResponse(repository.existsByUserIdAndIsActiveTrue(TenantContext.currentUserId()))
}
