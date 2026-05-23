package com.hazel.settings.controller

import com.hazel.settings.dto.PushStatusResponse
import com.hazel.settings.dto.PushSubscribeRequest
import com.hazel.settings.service.PushSubscriptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Push", description = "푸시 구독")
@RestController
@RequestMapping("/push")
class PushSubscriptionController(
    private val pushSubscriptionService: PushSubscriptionService,
) {
    @Operation(summary = "푸시 구독 등록", description = "endpoint(FCM 토큰) 기준 upsert")
    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun subscribe(
        @Valid @RequestBody request: PushSubscribeRequest,
    ) {
        pushSubscriptionService.subscribe(
            requireNotNull(request.endpoint),
            request.p256dh,
            request.auth,
            request.userAgent,
        )
    }

    @Operation(summary = "푸시 구독 해지")
    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsubscribe(
        @RequestParam endpoint: String,
    ) {
        pushSubscriptionService.unsubscribe(endpoint)
    }

    @Operation(summary = "푸시 구독 상태")
    @GetMapping("/status")
    fun status(): PushStatusResponse = pushSubscriptionService.status()
}
