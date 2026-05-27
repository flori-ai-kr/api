package kr.ai.flori.expenses.controller

import jakarta.validation.Valid
import kr.ai.flori.expenses.dto.*
import kr.ai.flori.expenses.service.RecurringExpenseService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 고정비 템플릿 + 인스턴스 분기(scope=this|all).
 */
@RestController
@RequestMapping("/recurring-expenses")
class RecurringExpenseController(
    private val service: RecurringExpenseService,
) {
    @GetMapping
    fun list(): List<RecurringExpenseResponse> = service.list()

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): RecurringExpenseResponse = service.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: RecurringExpenseRequest,
    ): RecurringExpenseResponse = service.create(request)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: RecurringExpenseRequest,
    ): RecurringExpenseResponse = service.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        service.delete(id)
    }

    @PostMapping("/{id}/toggle")
    fun toggle(
        @PathVariable id: Long,
        @Valid @RequestBody request: ToggleActiveRequest,
    ): RecurringExpenseResponse = service.toggleActive(id, requireNotNull(request.isActive))

    @PostMapping("/{id}/quick-add")
    @ResponseStatus(HttpStatus.CREATED)
    fun quickAdd(
        @PathVariable id: Long,
    ): ExpenseResponse = service.quickAdd(id)

    @PatchMapping("/instances/{expenseId}")
    fun updateInstance(
        @PathVariable expenseId: Long,
        @RequestParam(defaultValue = "this") scope: String,
        @Valid @RequestBody fields: RecurringInstanceUpdateRequest,
    ) {
        if (scope == SCOPE_ALL) {
            service.updateRecurringFromInstance(expenseId, fields)
        } else {
            service.updateInstanceOnly(expenseId, fields)
        }
    }

    @DeleteMapping("/instances/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteInstance(
        @PathVariable expenseId: Long,
        @RequestParam(defaultValue = "this") scope: String,
    ) {
        if (scope == SCOPE_ALL) {
            service.deleteRecurringFromInstance(expenseId)
        } else {
            service.deleteInstanceOnly(expenseId)
        }
    }

    private companion object {
        const val SCOPE_ALL = "all"
    }
}
