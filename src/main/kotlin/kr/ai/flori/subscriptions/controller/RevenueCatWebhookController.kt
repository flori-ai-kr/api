package kr.ai.flori.subscriptions.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.ai.flori.subscriptions.dto.RevenueCatWebhookRequest
import kr.ai.flori.subscriptions.dto.WebhookAck
import kr.ai.flori.subscriptions.security.RevenueCatWebhookVerifier
import kr.ai.flori.subscriptions.service.SubscriptionService
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 외부 결제 웹훅 수신. RevenueCat이 구매/갱신/취소/만료/환불 시 호출한다.
 * JWT가 아닌 사전 공유 Bearer 시크릿으로 인증(RevenueCatWebhookVerifier) — SecurityConfig에서 webhooks 경로를 공개한다.
 */
@Tag(name = "Webhooks", description = "외부 결제 웹훅 (Bearer 시크릿)")
@RestController
@RequestMapping("/webhooks")
class RevenueCatWebhookController(
    private val subscriptionService: SubscriptionService,
    private val verifier: RevenueCatWebhookVerifier,
) {
    @Operation(summary = "RevenueCat 구독 웹훅", description = "Bearer 시크릿 검증 후 이벤트 타입에 따라 구독 상태를 갱신한다.")
    @PostMapping("/revenuecat")
    fun revenuecat(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @Valid @RequestBody request: RevenueCatWebhookRequest,
    ): WebhookAck {
        verifier.verify(auth)
        subscriptionService.handleWebhook(requireNotNull(request.event))
        return WebhookAck(received = true)
    }
}
