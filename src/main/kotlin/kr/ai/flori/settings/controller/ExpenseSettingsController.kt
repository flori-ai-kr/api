package kr.ai.flori.settings.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.ai.flori.settings.dto.LabelSettingCreateRequest
import kr.ai.flori.settings.dto.LabelSettingResponse
import kr.ai.flori.settings.dto.LabelSettingUpdateRequest
import kr.ai.flori.settings.service.ExpenseCategorySettingService
import kr.ai.flori.settings.service.ExpensePaymentMethodSettingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "ExpenseSettings", description = "지출 설정(카테고리/결제방식)")
@RestController
@RequestMapping("/settings")
class ExpenseSettingsController(
    private val categoryService: ExpenseCategorySettingService,
    private val paymentService: ExpensePaymentMethodSettingService,
) {
    @Operation(summary = "지출 카테고리 목록")
    @GetMapping("/expense-categories")
    fun categories(): List<LabelSettingResponse> = categoryService.list()

    @Operation(summary = "지출 카테고리 생성")
    @PostMapping("/expense-categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(
        @Valid @RequestBody request: LabelSettingCreateRequest,
    ): LabelSettingResponse = categoryService.add(requireNotNull(request.label), request.color, request.value)

    @Operation(summary = "지출 카테고리 수정")
    @PutMapping("/expense-categories/{id}")
    fun updateCategory(
        @PathVariable id: UUID,
        @Valid @RequestBody request: LabelSettingUpdateRequest,
    ): LabelSettingResponse = categoryService.update(id, requireNotNull(request.label), requireNotNull(request.color))

    @Operation(summary = "지출 카테고리 삭제")
    @DeleteMapping("/expense-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(
        @PathVariable id: UUID,
    ) {
        categoryService.delete(id)
    }

    @Operation(summary = "지출 결제방식 목록")
    @GetMapping("/expense-payment-methods")
    fun payments(): List<LabelSettingResponse> = paymentService.list()

    @Operation(summary = "지출 결제방식 생성")
    @PostMapping("/expense-payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(
        @Valid @RequestBody request: LabelSettingCreateRequest,
    ): LabelSettingResponse = paymentService.add(requireNotNull(request.label), request.color, request.value)

    @Operation(summary = "지출 결제방식 수정")
    @PutMapping("/expense-payment-methods/{id}")
    fun updatePayment(
        @PathVariable id: UUID,
        @Valid @RequestBody request: LabelSettingUpdateRequest,
    ): LabelSettingResponse = paymentService.update(id, requireNotNull(request.label), requireNotNull(request.color))

    @Operation(summary = "지출 결제방식 삭제")
    @DeleteMapping("/expense-payment-methods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePayment(
        @PathVariable id: UUID,
    ) {
        paymentService.delete(id)
    }
}
