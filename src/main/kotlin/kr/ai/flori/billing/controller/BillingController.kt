package kr.ai.flori.billing.controller

import jakarta.validation.Valid
import kr.ai.flori.billing.dto.CardChangeRequest
import kr.ai.flori.billing.dto.MeResponse
import kr.ai.flori.billing.dto.PrepareResponse
import kr.ai.flori.billing.dto.SubscribeRequest
import kr.ai.flori.billing.dto.SubscriptionResponse
import kr.ai.flori.billing.service.SubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/billing")
class BillingController(
    private val subscriptionService: SubscriptionService,
) {
    @GetMapping("/prepare")
    fun prepare(): PrepareResponse = subscriptionService.prepare()

    @PostMapping("/subscribe")
    fun subscribe(
        @Valid @RequestBody request: SubscribeRequest,
    ): SubscriptionResponse = subscriptionService.subscribe(request)

    /** 카드 없이 30일 무료체험 시작. 요청 바디 없음. 멱등(이미 구독 있으면 그 구독 반환). */
    @PostMapping("/trial")
    fun startTrial(): SubscriptionResponse = subscriptionService.startTrial()

    @GetMapping("/me")
    fun me(): MeResponse = subscriptionService.me()

    @PostMapping("/cancel")
    fun cancel(): SubscriptionResponse = subscriptionService.cancel()

    @PostMapping("/resume")
    fun resume(): SubscriptionResponse = subscriptionService.resume()

    @PostMapping("/card")
    fun changeCard(
        @Valid @RequestBody request: CardChangeRequest,
    ): SubscriptionResponse = subscriptionService.changeCard(request)
}
