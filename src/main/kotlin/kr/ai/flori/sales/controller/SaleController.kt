package kr.ai.flori.sales.controller

import jakarta.validation.Valid
import kr.ai.flori.sales.dto.CompleteUnpaidRequest
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.dto.SaleResponse
import kr.ai.flori.sales.dto.SaleSuggestionsResponse
import kr.ai.flori.sales.dto.SaleUpdateRequest
import kr.ai.flori.sales.dto.SalesPageResponse
import kr.ai.flori.sales.service.SaleService
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

@RestController
@RequestMapping("/sales")
class SaleController(
    private val saleService: SaleService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) month: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) category: List<String>?,
        @RequestParam(required = false) payment: List<String>?,
        @RequestParam(required = false) channel: List<String>?,
        @RequestParam(required = false) search: String?,
    ): SalesPageResponse {
        val safeLimit = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return saleService.list(month, safeOffset, safeLimit, category, payment, channel, search)
    }

    @GetMapping("/suggestions")
    fun suggestions(): SaleSuggestionsResponse = SaleSuggestionsResponse(saleService.suggestions())

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): SaleResponse = saleService.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: SaleCreateRequest,
    ): SaleResponse = saleService.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: SaleUpdateRequest,
    ): SaleResponse = saleService.update(id, request)

    @PostMapping("/{id}/complete-unpaid")
    fun completeUnpaid(
        @PathVariable id: Long,
        @Valid @RequestBody request: CompleteUnpaidRequest,
    ): SaleResponse = saleService.completeUnpaid(id, requireNotNull(request.paymentMethod))

    @PostMapping("/{id}/revert-unpaid")
    fun revertUnpaid(
        @PathVariable id: Long,
    ): SaleResponse = saleService.revertUnpaid(id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        saleService.delete(id)
    }

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
}
