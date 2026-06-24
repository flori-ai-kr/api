package kr.ai.flori.support.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.support.dto.InquiryAnswerRequest
import kr.ai.flori.support.dto.InquiryResponse
import kr.ai.flori.support.dto.InquiryStatusRequest
import kr.ai.flori.support.service.SupportInquiryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 운영 콘솔 1:1 문의 인박스. cross-tenant — @RequiresAdmin 하위에서만 동작한다.
 * 답변/상태 변경은 감사 로그(INQUIRY_ANSWER/INQUIRY_STATUS)로 기록된다.
 */
@RestController
@RequestMapping("/admin/inquiries")
@RequiresAdmin
class AdminInquiryController(
    private val service: SupportInquiryService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<InquiryResponse> = service.listForAdmin(status, page, size)

    @GetMapping("/{id}")
    fun detail(
        @PathVariable id: Long,
    ): InquiryResponse = service.get(id)

    @PostMapping("/{id}/answer")
    fun answer(
        @PathVariable id: Long,
        @Valid @RequestBody request: InquiryAnswerRequest,
    ): InquiryResponse = service.answer(id, request)

    @PostMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: InquiryStatusRequest,
    ): InquiryResponse = service.changeStatus(id, requireNotNull(request.status))
}
