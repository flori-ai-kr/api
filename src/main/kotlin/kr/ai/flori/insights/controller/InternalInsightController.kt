package kr.ai.flori.insights.controller

import jakarta.validation.Valid
import kr.ai.flori.common.security.InternalAuthVerifier
import kr.ai.flori.insights.dto.*
import kr.ai.flori.insights.service.InsightIngestService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 내부 수집/관리 API. Bearer INTERNAL_API_KEY 인증(InternalAuthVerifier).
 */
@RestController
@RequestMapping("/internal")
class InternalInsightController(
    private val ingestService: InsightIngestService,
    private val authVerifier: InternalAuthVerifier,
) {
    @PostMapping("/trends")
    fun ingestTrends(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @Valid @RequestBody request: TrendArticlesBulkRequest,
    ): IngestResultResponse {
        authVerifier.verify(auth)
        return ingestService.ingestTrends(requireNotNull(request.articles))
    }

    @PostMapping("/instagram-posts")
    fun ingestPosts(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @Valid @RequestBody request: InstagramPostsBulkRequest,
    ): IngestResultResponse {
        authVerifier.verify(auth)
        return ingestService.ingestPosts(requireNotNull(request.posts))
    }

    @PostMapping("/instagram-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @Valid @RequestBody request: InstagramAccountCreateRequest,
    ): InstagramAccountResponse {
        authVerifier.verify(auth)
        return ingestService.createAccount(request)
    }

    @PutMapping("/instagram-accounts/{id}")
    fun updateAccount(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable id: Long,
        @Valid @RequestBody request: InstagramAccountUpdateRequest,
    ): InstagramAccountResponse {
        authVerifier.verify(auth)
        return ingestService.updateAccount(id, request)
    }

    @DeleteMapping("/instagram-accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable id: Long,
    ) {
        authVerifier.verify(auth)
        ingestService.deleteAccount(id)
    }
}
