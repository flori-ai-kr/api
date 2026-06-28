package kr.ai.flori.admin.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.ai.flori.admin.domain.PromptModels
import kr.ai.flori.admin.dto.PromptCreateRequest
import kr.ai.flori.admin.dto.PromptDetail
import kr.ai.flori.admin.dto.PromptSummary
import kr.ai.flori.admin.dto.PromptUpdateRequest
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminPromptService
import kr.ai.flori.ai.dto.BlogPreviewRequest
import kr.ai.flori.ai.dto.BlogPreviewResponse
import kr.ai.flori.ai.service.MarketingService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * AI 프롬프트 레지스트리 콘솔(SPEC-AI-008). cross-tenant 운영 도구 — @RequiresAdmin 게이트 하위에서만 호출된다.
 * 플레이그라운드 preview는 유저(어드민) JWT를 ai-server에 패스스루해 즉석 생성한다(저장 안 함).
 */
@RestController
@RequestMapping("/admin/prompts")
@RequiresAdmin
class AdminPromptController(
    private val service: AdminPromptService,
    private val marketingService: MarketingService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "blog") channel: String,
    ): List<PromptSummary> = service.list(channel)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): PromptDetail = service.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: PromptCreateRequest,
    ): PromptDetail = service.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: PromptUpdateRequest,
    ): PromptDetail = service.update(id, request)

    @PostMapping("/{id}/activate")
    fun activate(
        @PathVariable id: Long,
    ): PromptDetail = service.activate(id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        service.delete(id)
    }

    @PostMapping("/preview")
    fun preview(
        @Valid @RequestBody request: BlogPreviewRequest,
        httpRequest: HttpServletRequest,
    ): BlogPreviewResponse {
        PromptModels.validate(request.promptDraft.model)
        return marketingService.previewBlog(bearerToken(httpRequest), request)
    }

    /** 현재 요청의 유저(어드민) JWT 원문 — ai-server 도구 호출 패스스루용. */
    private fun bearerToken(request: HttpServletRequest): String {
        val header =
            request.getHeader(HttpHeaders.AUTHORIZATION)
                ?: throw AppException(CommonErrorCode.UNAUTHORIZED)
        if (!header.startsWith(BEARER_PREFIX)) throw AppException(CommonErrorCode.UNAUTHORIZED)
        return header.substring(BEARER_PREFIX.length)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
