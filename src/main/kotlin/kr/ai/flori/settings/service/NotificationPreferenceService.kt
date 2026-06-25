package kr.ai.flori.settings.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.dto.NotificationPreferenceResponse
import kr.ai.flori.settings.entity.NotificationPreference
import kr.ai.flori.settings.repository.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 푸시 타입별 수신 설정 조회/토글. 토글 가능 타입(PushTypes.TOGGLEABLE)만 다룬다.
 * 저장 안 된 타입은 기본 켜짐(true). 멀티테넌시: TenantContext userId 격리.
 */
@Service
class NotificationPreferenceService(
    private val repository: NotificationPreferenceRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<NotificationPreferenceResponse> {
        val saved = repository.findByUserId(TenantContext.currentUserId()).associateBy { it.type }
        return PushTypes.TOGGLEABLE.map { type ->
            NotificationPreferenceResponse(type = type, enabled = saved[type]?.enabled ?: true)
        }
    }

    @Transactional
    fun set(
        type: String,
        enabled: Boolean,
    ) {
        if (type !in PushTypes.TOGGLEABLE) throw AppException(CommonErrorCode.VALIDATION)
        val userId = TenantContext.currentUserId()
        val preference = repository.findByUserIdAndType(userId, type) ?: NotificationPreference(userId, type)
        preference.enabled = enabled
        repository.save(preference)
    }
}
