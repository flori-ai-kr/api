package kr.ai.flori.support.controller

import jakarta.validation.Valid
import kr.ai.flori.support.dto.InquiryCreateRequest
import kr.ai.flori.support.dto.InquiryResponse
import kr.ai.flori.support.dto.InquiryUploadRequest
import kr.ai.flori.support.dto.InquiryUploadTargetResponse
import kr.ai.flori.support.service.SupportInquiryService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 점주용 1:1 문의. JWT 인증만 필요(로그인한 점주면 누구나 제출 가능) —
 * @RequiresBusinessVerified/@RequiresAdmin 미적용. 본인 데이터만 조회한다.
 */
@RestController
@RequestMapping("/inquiries")
class InquiryController(
    private val service: SupportInquiryService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: InquiryCreateRequest,
    ): InquiryResponse = service.create(request)

    @GetMapping
    fun listMine(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): List<InquiryResponse> = service.listMine(page, size)

    @PostMapping("/upload-targets")
    fun uploadTargets(
        @RequestBody request: InquiryUploadRequest,
    ): List<InquiryUploadTargetResponse> = service.createUploadTargets(request.files)
}
