package kr.ai.flori.settings.controller

import jakarta.validation.Valid
import kr.ai.flori.settings.dto.PushStatusResponse
import kr.ai.flori.settings.dto.PushSubscribeRequest
import kr.ai.flori.settings.dto.PushTestResponse
import kr.ai.flori.settings.service.PushSubscriptionService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/push")
class PushSubscriptionController(
    private val pushSubscriptionService: PushSubscriptionService,
) {
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

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsubscribe(
        @RequestParam endpoint: String,
    ) {
        pushSubscriptionService.unsubscribe(endpoint)
    }

    @GetMapping("/status")
    fun status(): PushStatusResponse = pushSubscriptionService.status()

    @PostMapping("/test")
    fun test(): PushTestResponse = PushTestResponse(pushSubscriptionService.testPush())
}
