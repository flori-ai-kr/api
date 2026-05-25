package kr.ai.flori.subscriptions.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull
import kr.ai.flori.subscriptions.entity.Subscription
import java.time.Instant

/**
 * 현재 사용자 구독 상태. 앱은 이 값으로 프리미엄 UI/접근을 동기화한다(서버가 SSOT).
 * 구독 이력이 없으면 status="none" 의 빈 상태로 응답한다.
 */
data class SubscriptionResponse(
    val status: String,
    val active: Boolean,
    val entitlement: String?,
    val store: String?,
    val productId: String?,
    val currentPeriodEnd: Instant?,
) {
    companion object {
        /** 활성으로 간주하는 상태: 정상 구독(active) + 결제유예(in_grace). */
        val ACTIVE_STATES = setOf("active", "in_grace")

        fun none(): SubscriptionResponse = SubscriptionResponse("none", false, null, null, null, null)

        fun from(subscription: Subscription): SubscriptionResponse =
            SubscriptionResponse(
                status = subscription.status,
                active = subscription.status in ACTIVE_STATES,
                entitlement = subscription.entitlement,
                store = subscription.store,
                productId = subscription.productId,
                currentPeriodEnd = subscription.currentPeriodEnd,
            )
    }
}

/** RevenueCat 웹훅 페이로드. 최상위 `event` 객체 하나를 담는다. */
data class RevenueCatWebhookRequest(
    @field:NotNull(message = "event는 필수입니다")
    val event: RevenueCatEvent?,
)

/**
 * RevenueCat 웹훅 이벤트(snake_case). app_user_id 는 앱이 사용자 UUID로 설정한다.
 * @see <a href="https://www.revenuecat.com/docs/integrations/webhooks/event-types-and-fields">RevenueCat Webhook fields</a>
 */
data class RevenueCatEvent(
    val type: String?,
    @JsonProperty("app_user_id")
    val appUserId: String?,
    @JsonProperty("product_id")
    val productId: String?,
    val store: String?,
    @JsonProperty("entitlement_ids")
    val entitlementIds: List<String>? = null,
    @JsonProperty("entitlement_id")
    val entitlementId: String? = null,
    @JsonProperty("expiration_at_ms")
    val expirationAtMs: Long? = null,
    @JsonProperty("original_transaction_id")
    val originalTransactionId: String? = null,
    val id: String? = null,
    @JsonProperty("event_timestamp_ms")
    val eventTimestampMs: Long? = null,
)

/** 웹훅 수신 확인(ACK). RevenueCat 재시도 폭주를 막기 위해 처리 후 200으로 응답한다. */
data class WebhookAck(
    val received: Boolean,
)
