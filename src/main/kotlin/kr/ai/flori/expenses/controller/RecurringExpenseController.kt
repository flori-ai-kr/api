package kr.ai.flori.expenses.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.ai.flori.expenses.dto.ExpenseResponse
import kr.ai.flori.expenses.dto.RecurringExpenseRequest
import kr.ai.flori.expenses.dto.RecurringExpenseResponse
import kr.ai.flori.expenses.dto.RecurringInstanceUpdateRequest
import kr.ai.flori.expenses.dto.ToggleActiveRequest
import kr.ai.flori.expenses.service.RecurringExpenseService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 고정비 템플릿 + 인스턴스 분기(scope=this|all).
 */
@Tag(name = "RecurringExpenses", description = "고정비 관리")
@RestController
@RequestMapping("/recurring-expenses")
class RecurringExpenseController(
    private val service: RecurringExpenseService,
) {
    @Operation(summary = "고정비 목록")
    @GetMapping
    fun list(): List<RecurringExpenseResponse> = service.list()

    @Operation(summary = "고정비 단건 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): RecurringExpenseResponse = service.get(id)

    @Operation(summary = "고정비 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: RecurringExpenseRequest,
    ): RecurringExpenseResponse = service.create(request)

    @Operation(summary = "고정비 수정(전체 교체)")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RecurringExpenseRequest,
    ): RecurringExpenseResponse = service.update(id, request)

    @Operation(summary = "고정비 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) {
        service.delete(id)
    }

    @Operation(summary = "고정비 활성/비활성 토글")
    @PostMapping("/{id}/toggle")
    fun toggle(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ToggleActiveRequest,
    ): RecurringExpenseResponse = service.toggleActive(id, requireNotNull(request.isActive))

    @Operation(summary = "빠른 추가", description = "오늘 날짜로 즉시 지출 생성")
    @PostMapping("/{id}/quick-add")
    @ResponseStatus(HttpStatus.CREATED)
    fun quickAdd(
        @PathVariable id: UUID,
    ): ExpenseResponse = service.quickAdd(id)

    @Operation(summary = "인스턴스 수정", description = "scope=this(이것만) | all(이후 모두)")
    @PatchMapping("/instances/{expenseId}")
    fun updateInstance(
        @PathVariable expenseId: UUID,
        @RequestParam(defaultValue = "this") scope: String,
        @Valid @RequestBody fields: RecurringInstanceUpdateRequest,
    ) {
        if (scope == SCOPE_ALL) {
            service.updateRecurringFromInstance(expenseId, fields)
        } else {
            service.updateInstanceOnly(expenseId, fields)
        }
    }

    @Operation(summary = "인스턴스 삭제", description = "scope=this(이것만, skip) | all(이후 모두, end_date 단축)")
    @DeleteMapping("/instances/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteInstance(
        @PathVariable expenseId: UUID,
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
