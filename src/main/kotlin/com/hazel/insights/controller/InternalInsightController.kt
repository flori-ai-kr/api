package com.hazel.insights.controller

import com.hazel.insights.dto.IngestResultResponse
import com.hazel.insights.dto.InstagramAccountCreateRequest
import com.hazel.insights.dto.InstagramAccountResponse
import com.hazel.insights.dto.InstagramAccountUpdateRequest
import com.hazel.insights.dto.InstagramPostsBulkRequest
import com.hazel.insights.dto.TrendArticlesBulkRequest
import com.hazel.insights.service.InsightIngestService
import com.hazel.insights.service.InternalAuthVerifier
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 내부 수집/관리 API. Bearer INTERNAL_API_KEY 인증(InternalAuthVerifier).
 */
@Tag(name = "Internal", description = "내부 수집/관리 (Bearer 키)")
@RestController
@RequestMapping("/internal")
class InternalInsightController(
    private val ingestService: InsightIngestService,
    private val authVerifier: InternalAuthVerifier,
) {
    @Operation(summary = "트렌드 수집", description = "멱등(source_url 중복 스킵) + 신규 시 브로드캐스트")
    @PostMapping("/trends")
    fun ingestTrends(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @Valid @RequestBody request: TrendArticlesBulkRequest,
    ): IngestResultResponse {
        authVerifier.verify(auth)
        return ingestService.ingestTrends(requireNotNull(request.articles))
    }

    @Operation(summary = "인스타 포스트 수집", description = "멱등(shortcode 중복 스킵)")
    @PostMapping("/instagram-posts")
    fun ingestPosts(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @Valid @RequestBody request: InstagramPostsBulkRequest,
    ): IngestResultResponse {
        authVerifier.verify(auth)
        return ingestService.ingestPosts(requireNotNull(request.posts))
    }

    @Operation(summary = "인스타 계정 등록")
    @PostMapping("/instagram-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @Valid @RequestBody request: InstagramAccountCreateRequest,
    ): InstagramAccountResponse {
        authVerifier.verify(auth)
        return ingestService.createAccount(request)
    }

    @Operation(summary = "인스타 계정 수정")
    @PutMapping("/instagram-accounts/{id}")
    fun updateAccount(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable id: UUID,
        @Valid @RequestBody request: InstagramAccountUpdateRequest,
    ): InstagramAccountResponse {
        authVerifier.verify(auth)
        return ingestService.updateAccount(id, request)
    }

    @Operation(summary = "인스타 계정 삭제")
    @DeleteMapping("/instagram-accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable id: UUID,
    ) {
        authVerifier.verify(auth)
        ingestService.deleteAccount(id)
    }
}
