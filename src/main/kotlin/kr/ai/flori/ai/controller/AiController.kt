package kr.ai.flori.ai.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.ai.flori.ai.dto.ChatRequest
import kr.ai.flori.ai.dto.ChatResponse
import kr.ai.flori.ai.dto.ConfirmRequest
import kr.ai.flori.ai.dto.ConfirmResponse
import kr.ai.flori.ai.dto.ConfirmationCardResponse
import kr.ai.flori.ai.dto.OcrReservationRequest
import kr.ai.flori.ai.dto.ProactiveResponse
import kr.ai.flori.ai.service.AiService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * AI 게이트웨이 엔드포인트. web은 여기만 호출한다(ai-server는 내부망 — web 미노출).
 * 인증은 표준 유저 JWT(SecurityConfig) — 게이트웨이가 그 JWT를 ai-server로 포워딩해 도구 호출에 쓰게 한다.
 */
@RestController
@RequestMapping("/ai")
class AiController(
    private val aiService: AiService,
) {
    @PostMapping("/chat")
    fun chat(
        @Valid @RequestBody request: ChatRequest,
        httpRequest: HttpServletRequest,
    ): ChatResponse = aiService.chat(bearerToken(httpRequest), request)

    @GetMapping("/proactive")
    fun proactive(httpRequest: HttpServletRequest): ProactiveResponse = aiService.proactive(bearerToken(httpRequest))

    @PostMapping("/ocr/reservation")
    fun ocrReservation(
        @Valid @RequestBody request: OcrReservationRequest,
        httpRequest: HttpServletRequest,
    ): ConfirmationCardResponse = aiService.proposeOcrReservation(bearerToken(httpRequest), request)

    @PostMapping("/confirm")
    fun confirm(
        @Valid @RequestBody request: ConfirmRequest,
    ): ConfirmResponse = aiService.confirm(request)

    /** 현재 요청의 유저 JWT 원문(ai-server 도구 호출 패스스루용). */
    private fun bearerToken(request: HttpServletRequest): String {
        val header =
            request.getHeader(HttpHeaders.AUTHORIZATION)
                ?: throw AppException(CommonErrorCode.UNAUTHORIZED)
        if (!header.startsWith(BEARER_PREFIX)) {
            throw AppException(CommonErrorCode.UNAUTHORIZED)
        }
        return header.substring(BEARER_PREFIX.length)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
