package kr.ai.flori.settings.controller

import jakarta.validation.Valid
import kr.ai.flori.settings.dto.LabelSettingCreateRequest
import kr.ai.flori.settings.dto.LabelSettingResponse
import kr.ai.flori.settings.dto.LabelSettingUpdateRequest
import kr.ai.flori.settings.service.SaleCategorySettingService
import kr.ai.flori.settings.service.SaleChannelSettingService
import kr.ai.flori.settings.service.SalePaymentMethodSettingService
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

@RestController
@RequestMapping("/settings")
class SaleSettingsController(
    private val categoryService: SaleCategorySettingService,
    private val paymentService: SalePaymentMethodSettingService,
    private val channelService: SaleChannelSettingService,
) {
    @GetMapping("/sale-categories")
    fun categories(): List<LabelSettingResponse> = categoryService.list()

    @PostMapping("/sale-categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(
        @Valid @RequestBody request: LabelSettingCreateRequest,
    ): LabelSettingResponse = categoryService.add(requireNotNull(request.label), request.value)

    @PutMapping("/sale-categories/{id}")
    fun updateCategory(
        @PathVariable id: Long,
        @Valid @RequestBody request: LabelSettingUpdateRequest,
    ): LabelSettingResponse = categoryService.update(id, requireNotNull(request.label))

    @DeleteMapping("/sale-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(
        @PathVariable id: Long,
    ) {
        categoryService.delete(id)
    }

    @GetMapping("/payment-methods")
    fun payments(): List<LabelSettingResponse> = paymentService.list()

    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(
        @Valid @RequestBody request: LabelSettingCreateRequest,
    ): LabelSettingResponse = paymentService.add(requireNotNull(request.label), request.value)

    @PutMapping("/payment-methods/{id}")
    fun updatePayment(
        @PathVariable id: Long,
        @Valid @RequestBody request: LabelSettingUpdateRequest,
    ): LabelSettingResponse = paymentService.update(id, requireNotNull(request.label))

    @DeleteMapping("/payment-methods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePayment(
        @PathVariable id: Long,
    ) {
        paymentService.delete(id)
    }

    @GetMapping("/sale-channels")
    fun channels(): List<LabelSettingResponse> = channelService.list()

    @PostMapping("/sale-channels")
    @ResponseStatus(HttpStatus.CREATED)
    fun createChannel(
        @Valid @RequestBody request: LabelSettingCreateRequest,
    ): LabelSettingResponse = channelService.add(requireNotNull(request.label), request.value)

    @PutMapping("/sale-channels/{id}")
    fun updateChannel(
        @PathVariable id: Long,
        @Valid @RequestBody request: LabelSettingUpdateRequest,
    ): LabelSettingResponse = channelService.update(id, requireNotNull(request.label))

    @DeleteMapping("/sale-channels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteChannel(
        @PathVariable id: Long,
    ) {
        channelService.delete(id)
    }
}
