package kr.ai.flori.subscriptions.controller

import kr.ai.flori.subscriptions.dto.SubscriptionResponse
import kr.ai.flori.subscriptions.gating.RequiresSubscription
import kr.ai.flori.subscriptions.service.SubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
) {
    @GetMapping("/subscription")
    fun current(): SubscriptionResponse = subscriptionService.getCurrent()

    @RequiresSubscription
    @GetMapping("/subscription/premium-example")
    fun premiumExample(): Map<String, String> = mapOf("message" to "프리미엄 기능 접근이 허용되었습니다")
}
