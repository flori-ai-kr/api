package kr.ai.flori.expenses.controller

import jakarta.validation.Valid
import kr.ai.flori.expenses.dto.ExpenseCreateRequest
import kr.ai.flori.expenses.dto.ExpenseResponse
import kr.ai.flori.expenses.dto.ExpenseSuggestionsResponse
import kr.ai.flori.expenses.dto.ExpenseUpdateRequest
import kr.ai.flori.expenses.service.ExpenseService
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
@RequestMapping("/expenses")
class ExpenseController(
    private val expenseService: ExpenseService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) month: String?,
    ): List<ExpenseResponse> = expenseService.list(month)

    @GetMapping("/suggestions")
    fun suggestions(): ExpenseSuggestionsResponse = expenseService.suggestions()

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ExpenseResponse = expenseService.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ExpenseCreateRequest,
    ): ExpenseResponse = expenseService.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: ExpenseUpdateRequest,
    ): ExpenseResponse = expenseService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        expenseService.delete(id)
    }
}
