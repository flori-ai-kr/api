package kr.ai.flori.sales.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
import java.util.UUID

@Tag(name = "Sales", description = "매출 관리")
@RestController
@RequestMapping("/sales")
class SaleController(
    private val saleService: SaleService,
) {
    @Operation(summary = "매출 목록", description = "무한스크롤(offset/limit) + 월/다중선택 필터 + 검색")
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

    @Operation(summary = "비고 자동완성", description = "과거 비고를 빈도순으로 반환")
    @GetMapping("/suggestions")
    fun suggestions(): SaleSuggestionsResponse = SaleSuggestionsResponse(saleService.suggestions())

    @Operation(summary = "매출 단건 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): SaleResponse = saleService.get(id)

    @Operation(summary = "매출 생성", description = "수수료/입금예정일/입금상태는 서버가 계산")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: SaleCreateRequest,
    ): SaleResponse = saleService.create(request)

    @Operation(summary = "매출 수정", description = "제공된 필드만 반영, 입금값 재계산")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SaleUpdateRequest,
    ): SaleResponse = saleService.update(id, request)

    @Operation(summary = "미수 완료", description = "미수 매출의 결제방식을 확정")
    @PostMapping("/{id}/complete-unpaid")
    fun completeUnpaid(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CompleteUnpaidRequest,
    ): SaleResponse = saleService.completeUnpaid(id, requireNotNull(request.paymentMethod))

    @Operation(summary = "미수 되돌리기", description = "결제방식을 다시 미수로")
    @PostMapping("/{id}/revert-unpaid")
    fun revertUnpaid(
        @PathVariable id: UUID,
    ): SaleResponse = saleService.revertUnpaid(id)

    @Operation(summary = "매출 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) {
        saleService.delete(id)
    }

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
}
