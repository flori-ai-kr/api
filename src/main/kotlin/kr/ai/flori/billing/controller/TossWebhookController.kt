package kr.ai.flori.billing.controller

import kr.ai.flori.billing.dto.TossWebhookEvent
import kr.ai.flori.billing.service.TossWebhookService
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Suppress("TooGenericExceptionCaught")
    fun receive(
        @RequestBody event: TossWebhookEvent,
    ) {
        try {
            tossWebhookService.handle(event)
        } catch (e: Exception) {
            log.error("토스 웹훅 처리 실패(200 응답 유지): {}", e.message, e)
        }
    }
}
