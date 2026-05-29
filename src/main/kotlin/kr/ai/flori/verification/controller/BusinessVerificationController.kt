package kr.ai.flori.verification.controller

import jakarta.validation.Valid
import kr.ai.flori.verification.dto.BusinessLicenseUploadTargetRequest
import kr.ai.flori.verification.dto.BusinessLicenseUploadTargetResponse
import kr.ai.flori.verification.dto.BusinessVerificationResponse
import kr.ai.flori.verification.dto.BusinessVerificationSubmitRequest
import kr.ai.flori.verification.service.BusinessVerificationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사업자 인증. 모든 엔드포인트 JWT 인증, 신원은 TenantContext에서만 도출.
 * 이 엔드포인트들은 게이팅(@RequiresBusinessVerified) 대상이 아니다(인증 입구).
 */
@RestController
@RequestMapping("/verification/business")
class BusinessVerificationController(
    private val service: BusinessVerificationService,
) {
    @PostMapping("/upload-target")
    fun uploadTarget(
        @Valid @RequestBody request: BusinessLicenseUploadTargetRequest,
    ): BusinessLicenseUploadTargetResponse = service.createUploadTarget(requireNotNull(request.contentType))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @Valid @RequestBody request: BusinessVerificationSubmitRequest,
    ): BusinessVerificationResponse = service.submit(request)

    @GetMapping("/me")
    fun myStatus(): BusinessVerificationResponse = service.getMyStatus()
}
