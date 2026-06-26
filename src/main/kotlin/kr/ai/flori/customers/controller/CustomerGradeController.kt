package kr.ai.flori.customers.controller

import jakarta.validation.Valid
import kr.ai.flori.customers.dto.CustomerGradeCreateRequest
import kr.ai.flori.customers.dto.CustomerGradeResponse
import kr.ai.flori.customers.dto.CustomerGradeUpdateRequest
import kr.ai.flori.customers.dto.GradeRecomputePreviewResponse
import kr.ai.flori.customers.dto.GradeThresholdPreviewRequest
import kr.ai.flori.customers.service.CustomerGradeService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/customer-grades")
class CustomerGradeController(
    private val service: CustomerGradeService,
) {
    @GetMapping
    fun list(): List<CustomerGradeResponse> = service.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody req: CustomerGradeCreateRequest,
    ): CustomerGradeResponse = service.create(req)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody req: CustomerGradeUpdateRequest,
    ): CustomerGradeResponse = service.update(id, req)

    /** 임계값 변경 미리보기 — 저장 없이, 이 변경으로 등급이 바뀔 고객 목록을 반환. */
    @PostMapping("/{id}/preview")
    fun previewThresholdChange(
        @PathVariable id: Long,
        @Valid @RequestBody req: GradeThresholdPreviewRequest,
    ): GradeRecomputePreviewResponse {
        val newThreshold = if (req.clearThreshold) null else req.threshold
        val changes = service.previewThresholdChange(id, newThreshold)
        return GradeRecomputePreviewResponse(total = changes.size, changes = changes)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        service.delete(id)
    }
}
