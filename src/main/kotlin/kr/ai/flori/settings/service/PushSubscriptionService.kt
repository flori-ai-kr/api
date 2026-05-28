package kr.ai.flori.settings.service

import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.dto.PushStatusResponse
import kr.ai.flori.settings.entity.PushSubscription
import kr.ai.flori.settings.repository.PushSubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 푸시 구독 등록/해지/상태. endpoint(FCM 토큰) 기준 upsert.
 */
@Service
class PushSubscriptionService(
    private val repository: PushSubscriptionRepository,
    private val pushDispatcher: PushDispatcher,
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

    /** 현재 사용자의 활성 구독에 테스트 페이로드를 발송한다. 영구 실패 구독은 자동 비활성화. */
    fun testPush(): Int =
        pushDispatcher.sendToUser(
            userId = TenantContext.currentUserId(),
            title = "테스트 알림",
            body = "푸시 알림이 정상적으로 동작합니다",
            url = "/",
        )
}
