package com.hazel.deposits.controller

import com.hazel.deposits.dto.ConfirmDepositsRequest
import com.hazel.deposits.dto.DepositSummaryResponse
import com.hazel.deposits.service.DepositService
import com.hazel.sales.dto.SaleResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Deposits", description = "카드 입금대조")
@RestController
@RequestMapping("/deposits")
class DepositController(
    private val depositService: DepositService,
) {
    @Operation(summary = "입금 목록", description = "카드 매출, status(pending/completed/all)·card_company·month 필터")
    @GetMapping
    fun list(
        @RequestParam(required = false) month: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cardCompany: String?,
    ): List<SaleResponse> = depositService.list(month, status, cardCompany)

    @Operation(summary = "입금 요약", description = "대기/완료 건수·금액")
    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) month: String?,
    ): DepositSummaryResponse = depositService.summary(month)

    @Operation(summary = "입금 확인", description = "completed + deposited_at 기록")
    @PostMapping("/{id}/confirm")
    fun confirm(
        @PathVariable id: UUID,
    ): SaleResponse = depositService.confirm(id)

    @Operation(summary = "입금 다건 확인")
    @PostMapping("/confirm")
    fun confirmMultiple(
        @Valid @RequestBody request: ConfirmDepositsRequest,
    ): Map<String, Int> = mapOf("confirmed" to depositService.confirmMultiple(requireNotNull(request.ids)))

    @Operation(summary = "입금 되돌리기", description = "pending + deposited_at 제거")
    @PostMapping("/{id}/revert")
    fun revert(
        @PathVariable id: UUID,
    ): SaleResponse = depositService.revert(id)
}
