package com.hazel.subscriptions.controller

import com.hazel.subscriptions.dto.SubscriptionResponse
import com.hazel.subscriptions.gating.RequiresSubscription
import com.hazel.subscriptions.service.SubscriptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Subscription", description = "구독 상태 조회 + 프리미엄 게이팅")
@RestController
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
) {
    @Operation(summary = "현재 구독 상태", description = "active/in_grace/expired/none + 만료일/티어. 앱이 프리미엄 동기화에 사용.")
    @GetMapping("/subscription")
    fun current(): SubscriptionResponse = subscriptionService.getCurrent()

    @Operation(summary = "[예시] 프리미엄 전용 기능", description = "활성 구독이 없으면 403. 게이팅 적용 지점 예시(@RequiresSubscription).")
    @RequiresSubscription
    @GetMapping("/subscription/premium-example")
    fun premiumExample(): Map<String, String> = mapOf("message" to "프리미엄 기능 접근이 허용되었습니다")
}
