package kr.ai.flori.ai.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.ai.flori.ai.dto.BlogGenerateRequest
import kr.ai.flori.ai.dto.BlogGenerateResponse
import kr.ai.flori.ai.dto.MarketingContentDetail
import kr.ai.flori.ai.dto.MarketingContentUpdateRequest
import kr.ai.flori.ai.dto.MarketingContentsResponse
import kr.ai.flori.ai.dto.ToneProfileResponse
import kr.ai.flori.ai.dto.ToneProfileUpdateRequest
import kr.ai.flori.ai.service.MarketingService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * AI 마케팅 게이트웨이 엔드포인트. web은 여기만 호출한다(ai-server는 내부망 — web 미노출).
 * 블로그 초안 생성은 유저 JWT를 ai-server로 포워딩(도구/맥락 인증 패스스루)한다.
 */
@RestController
@RequestMapping("/ai/marketing")
class MarketingController(
    private val marketingService: MarketingService,
) {
    @PostMapping("/blog")
    fun generateBlog(
        @Valid @RequestBody request: BlogGenerateRequest,
        httpRequest: HttpServletRequest,
    ): BlogGenerateResponse = marketingService.generateBlog(bearerToken(httpRequest), request)

    @GetMapping("/tone-profile")
    fun getToneProfile(): ToneProfileResponse = marketingService.getToneProfile()

    @PutMapping("/tone-profile")
    fun updateToneProfile(
        @Valid @RequestBody request: ToneProfileUpdateRequest,
    ): ToneProfileResponse = marketingService.updateToneProfile(request)

    @GetMapping("/contents")
    fun listContents(
        @RequestParam(defaultValue = "blog") channel: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): MarketingContentsResponse = marketingService.listContents(channel, offset, limit)

    @GetMapping("/contents/{id}")
    fun getContent(
        @PathVariable id: Long,
    ): MarketingContentDetail = marketingService.getContent(id)

    @PutMapping("/contents/{id}")
    fun updateContent(
        @PathVariable id: Long,
        @Valid @RequestBody request: MarketingContentUpdateRequest,
    ): MarketingContentDetail = marketingService.updateContent(id, request)

    @DeleteMapping("/contents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContent(
        @PathVariable id: Long,
    ) {
        marketingService.deleteContent(id)
    }

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
