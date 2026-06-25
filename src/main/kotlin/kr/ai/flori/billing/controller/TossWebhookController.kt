package kr.ai.flori.billing.controller

import kr.ai.flori.billing.dto.TossWebhookEvent
import kr.ai.flori.billing.service.TossWebhookService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 토스 결제 웹훅. 공개 엔드포인트(본문 비신뢰, 재조회로 검증). 항상 200. */
@RestController
@RequestMapping("/webhooks/toss")
class TossWebhookController(
    private val tossWebhookService: TossWebhookService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun receive(
        @RequestBody event: TossWebhookEvent,
    ) {
        tossWebhookService.handle(event)
    }
}
