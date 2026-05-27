package kr.ai.flori.settings.controller

import jakarta.validation.Valid
import kr.ai.flori.settings.dto.LabelSettingCreateRequest
import kr.ai.flori.settings.dto.LabelSettingResponse
import kr.ai.flori.settings.dto.LabelSettingUpdateRequest
import kr.ai.flori.settings.service.ExpenseCategorySettingService
import kr.ai.flori.settings.service.ExpensePaymentMethodSettingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/settings")
class ExpenseSettingsController(
    private val categoryService: ExpenseCategorySettingService,
    private val paymentService: ExpensePaymentMethodSettingService,
) {
    @GetMapping("/expense-categories")
    fun categories(): List<LabelSettingResponse> = categoryService.list()

    @PostMapping("/expense-categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(
        @Valid @RequestBody request: LabelSettingCreateRequest,
    ): LabelSettingResponse = categoryService.add(requireNotNull(request.label), request.color, request.value)

    @PutMapping("/expense-categories/{id}")
    fun updateCategory(
        @PathVariable id: Long,
        @Valid @RequestBody request: LabelSettingUpdateRequest,
    ): LabelSettingResponse = categoryService.update(id, requireNotNull(request.label), requireNotNull(request.color))

    @DeleteMapping("/expense-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(
        @PathVariable id: Long,
    ) {
        categoryService.delete(id)
    }

    @GetMapping("/expense-payment-methods")
    fun payments(): List<LabelSettingResponse> = paymentService.list()

    @PostMapping("/expense-payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(
        @Valid @RequestBody request: LabelSettingCreateRequest,
    ): LabelSettingResponse = paymentService.add(requireNotNull(request.label), request.color, request.value)

    @PutMapping("/expense-payment-methods/{id}")
    fun updatePayment(
        @PathVariable id: Long,
        @Valid @RequestBody request: LabelSettingUpdateRequest,
    ): LabelSettingResponse = paymentService.update(id, requireNotNull(request.label), requireNotNull(request.color))

    @DeleteMapping("/expense-payment-methods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePayment(
        @PathVariable id: Long,
    ) {
        paymentService.delete(id)
    }
}
