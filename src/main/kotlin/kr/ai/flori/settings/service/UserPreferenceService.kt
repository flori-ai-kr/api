package kr.ai.flori.settings.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.dto.UserPreferencesResponse
import kr.ai.flori.settings.entity.UserPreference
import kr.ai.flori.settings.repository.UserPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 유저 설정. key-value 스토어(`user_preferences`) 위에서 동작 — 설정마다 key 한 행, value는 jsonb.
 * 없으면 기본값 반환, 변경은 upsert. 멀티테넌시: 항상 [TenantContext.currentUserId]를 키로 조회.
 */
@Service
class UserPreferenceService(
    private val repository: UserPreferenceRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun get(): UserPreferencesResponse {
        val pref = repository.findByUserIdAndKey(TenantContext.currentUserId(), BOTTOM_NAV_KEY)
        // value(jsonb)가 손상/형식 불일치여도 조회가 500으로 깨지지 않도록 기본값으로 폴백.
        val items =
            pref?.let { runCatching { objectMapper.readValue<List<String>>(it.value) }.getOrNull() }
                ?: DEFAULT_BOTTOM_NAV
        return UserPreferencesResponse(items)
    }

    @Transactional
    fun updateBottomNav(items: List<String>): UserPreferencesResponse {
        val userId = TenantContext.currentUserId()
        val pref =
            repository.findByUserIdAndKey(userId, BOTTOM_NAV_KEY)
                ?: UserPreference(userId, BOTTOM_NAV_KEY)
        pref.value = objectMapper.writeValueAsString(items)
        repository.save(pref)
        return UserPreferencesResponse(items)
    }

    companion object {
        /** 하단바 설정 key. */
        const val BOTTOM_NAV_KEY = "bottom_nav"

        /** 가입 시 시드/기본 하단바 항목(SSOT). DefaultDataSeeder도 동일 값을 시드한다. */
        val DEFAULT_BOTTOM_NAV = listOf("dashboard", "sales", "customers", "calendar", "statistics", "insights")
    }
}
