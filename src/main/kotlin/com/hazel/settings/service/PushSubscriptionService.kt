package com.hazel.settings.service

import com.hazel.common.tenant.TenantContext
import com.hazel.settings.dto.PushStatusResponse
import com.hazel.settings.entity.PushSubscription
import com.hazel.settings.repository.PushSubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
        // 멀티테넌시: 본인 소유 구독만 갱신한다. 같은 endpoint(FCM 토큰, 전역 unique)가
        // 다른 사용자 소유면 신규 INSERT가 unique 제약에 걸려 409가 되며, 타 사용자 구독을
        // 가로채거나 덮어쓰지 않는다.(findByEndpoint 전역 조회는 의도적으로 사용하지 않음)
        val subscription = repository.findByUserIdAndEndpoint(userId, endpoint) ?: PushSubscription(userId, endpoint)
        subscription.p256dh = p256dh
        subscription.auth = auth
        subscription.userAgent = userAgent
        subscription.isActive = true
        repository.save(subscription)
    }

    @Transactional
    fun unsubscribe(endpoint: String) {
        repository.findByUserIdAndEndpoint(TenantContext.currentUserId(), endpoint)?.let {
            it.isActive = false
            repository.save(it)
        }
    }

    @Transactional(readOnly = true)
    fun status(): PushStatusResponse = PushStatusResponse(repository.existsByUserIdAndIsActiveTrue(TenantContext.currentUserId()))
}
