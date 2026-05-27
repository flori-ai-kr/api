package kr.ai.flori.settings.service

import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.dto.UserPreferencesResponse
import kr.ai.flori.settings.entity.UserPreferences
import kr.ai.flori.settings.repository.UserPreferencesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 유저 설정(하단바). 없으면 기본값 반환, 변경은 upsert.
 *
 * 멀티테넌시: UserPreferences의 PK가 user_id이므로 항상 [TenantContext.currentUserId]를 키로 조회한다
 * (임의 Long를 키로 넘기지 말 것 — 격리가 호출부 규약에 의존한다).
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
