package kr.ai.flori.subscriptions.controller

import kr.ai.flori.subscriptions.dto.SubscriptionResponse
import kr.ai.flori.subscriptions.service.SubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
) {
    @GetMapping("/subscription")
    fun current(): SubscriptionResponse = subscriptionService.getCurrent()
}
