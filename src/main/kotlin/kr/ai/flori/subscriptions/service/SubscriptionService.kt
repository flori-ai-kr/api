package kr.ai.flori.subscriptions.service

import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.subscriptions.dto.RevenueCatEvent
import kr.ai.flori.subscriptions.dto.SubscriptionResponse
import kr.ai.flori.subscriptions.entity.Subscription
import kr.ai.flori.subscriptions.entity.SubscriptionEvent
import kr.ai.flori.subscriptions.repository.SubscriptionEventRepository
import kr.ai.flori.subscriptions.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 구독 상태 서비스. 서버가 구독 상태의 SSOT.
 * - 웹훅 수신 → 이벤트 이력 기록 + 현재 상태 갱신.
 * - 조회/게이팅은 TenantContext userId로 격리(멀티테넌시 HARD).
 */
@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val eventRepository: SubscriptionEventRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 현재 사용자 구독 상태(없으면 none). */
    @Transactional(readOnly = true)
    fun getCurrent(): SubscriptionResponse =
        subscriptionRepository
            .findByUserId(TenantContext.currentUserId())
            ?.let(SubscriptionResponse::from)
            ?: SubscriptionResponse.none()

    /** 게이팅 헬퍼: 활성(active/in_grace) 구독 보유 여부. */
    @Transactional(readOnly = true)
    fun hasActiveSubscription(userId: Long): Boolean =
        subscriptionRepository.findByUserId(userId)?.status in SubscriptionResponse.ACTIVE_STATES

    /** 게이팅 가드: 현재 사용자가 활성 구독이 없으면 403. */
    @Transactional(readOnly = true)
    fun requireActiveSubscription() {
        if (!hasActiveSubscription(TenantContext.currentUserId())) {
            throw AppException(ErrorCode.FORBIDDEN, "구독이 필요한 기능입니다")
        }
    }

    /**
     * RevenueCat 웹훅 처리(멱등 지향). 이벤트 이력을 항상 기록하고, 매핑되는 상태가 있으면 현재 구독을 upsert 한다.
     * app_user_id가 유효한 사용자가 아니면 상태 갱신은 건너뛰되 이벤트는 익명(user_id=null)으로 기록한다.
     */
    @Transactional
    fun handleWebhook(event: RevenueCatEvent) {
        val userId = resolveUserId(event.appUserId)
        recordEvent(event, userId)

        val newStatus = SubscriptionStatusMapper.mapStatus(event.type)
        if (userId == null || newStatus == null) {
            log.info("구독 웹훅 상태변경 없음: type={} userId={} status={}", event.type, userId, newStatus)
            return
        }
        upsert(userId, event, newStatus)
    }

    private fun upsert(
        userId: Long,
        event: RevenueCatEvent,
        newStatus: String,
    ) {
        val existing = subscriptionRepository.findByUserId(userId)
        val subscription =
            existing ?: Subscription(
                userId = userId,
                store = SubscriptionStatusMapper.mapStore(event.store),
                productId = event.productId ?: "unknown",
            )
        subscription.status = newStatus
        subscription.store = SubscriptionStatusMapper.mapStore(event.store)
        event.productId?.let { subscription.productId = it }
        subscription.entitlement = resolveEntitlement(event, existing?.entitlement)
        event.originalTransactionId?.let { subscription.originalTransactionId = it }
        event.expirationAtMs?.let { subscription.currentPeriodEnd = Instant.ofEpochMilli(it) }
        subscriptionRepository.save(subscription)
    }

    private fun recordEvent(
        event: RevenueCatEvent,
        userId: Long?,
    ) {
        val entity =
            SubscriptionEvent(
                userId = userId,
                eventType = event.type ?: "UNKNOWN",
            )
        entity.eventId = event.id
        entity.store = event.store
        entity.productId = event.productId
        entity.rawEvent = runCatching { objectMapper.writeValueAsString(event) }.getOrNull()
        entity.occurredAt = event.eventTimestampMs?.let(Instant::ofEpochMilli)
        eventRepository.save(entity)
    }

    private fun resolveUserId(appUserId: String?): Long? {
        val userId = appUserId?.let { it.toLongOrNull() } ?: return null
        return if (userRepository.existsById(userId)) userId else null
    }

    private fun resolveEntitlement(
        event: RevenueCatEvent,
        fallback: String?,
    ): String = event.entitlementId ?: event.entitlementIds?.firstOrNull() ?: fallback ?: "premium"
}
