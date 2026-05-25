package kr.ai.flori.deposits.controller

import jakarta.validation.Valid
import kr.ai.flori.deposits.dto.ConfirmDepositsRequest
import kr.ai.flori.deposits.dto.DepositSummaryResponse
import kr.ai.flori.deposits.service.DepositService
import kr.ai.flori.sales.dto.SaleResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/deposits")
class DepositController(
    private val depositService: DepositService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) month: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cardCompany: String?,
    ): List<SaleResponse> = depositService.list(month, status, cardCompany)

    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) month: String?,
    ): DepositSummaryResponse = depositService.summary(month)

    @PostMapping("/{id}/confirm")
    fun confirm(
        @PathVariable id: UUID,
    ): SaleResponse = depositService.confirm(id)

    @PostMapping("/confirm")
    fun confirmMultiple(
        @Valid @RequestBody request: ConfirmDepositsRequest,
    ): Map<String, Int> = mapOf("confirmed" to depositService.confirmMultiple(requireNotNull(request.ids)))

    @PostMapping("/{id}/revert")
    fun revert(
        @PathVariable id: UUID,
    ): SaleResponse = depositService.revert(id)
}
