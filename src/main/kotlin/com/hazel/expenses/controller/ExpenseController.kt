package com.hazel.expenses.controller

import com.hazel.expenses.dto.ExpenseCreateRequest
import com.hazel.expenses.dto.ExpenseResponse
import com.hazel.expenses.dto.ExpenseSuggestionsResponse
import com.hazel.expenses.dto.ExpenseUpdateRequest
import com.hazel.expenses.service.ExpenseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import java.util.UUID

@Tag(name = "Expenses", description = "지출 관리")
@RestController
@RequestMapping("/expenses")
class ExpenseController(
    private val expenseService: ExpenseService,
) {
    @Operation(summary = "지출 목록", description = "월(연/월/일) 필터")
    @GetMapping
    fun list(
        @RequestParam(required = false) month: String?,
    ): List<ExpenseResponse> = expenseService.list(month)

    @Operation(summary = "지출 자동완성", description = "물품명/거래처/비고 빈도순")
    @GetMapping("/suggestions")
    fun suggestions(): ExpenseSuggestionsResponse = expenseService.suggestions()

    @Operation(summary = "지출 단건 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ExpenseResponse = expenseService.get(id)

    @Operation(summary = "지출 생성", description = "총액 = 단가 * 수량 (서버 계산)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ExpenseCreateRequest,
    ): ExpenseResponse = expenseService.create(request)

    @Operation(summary = "지출 수정", description = "제공된 필드만 반영, 총액 재계산")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ExpenseUpdateRequest,
    ): ExpenseResponse = expenseService.update(id, request)

    @Operation(summary = "지출 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) {
        expenseService.delete(id)
    }
}
