package com.hazel.customers.controller

import com.hazel.customers.dto.CustomerCreateRequest
import com.hazel.customers.dto.CustomerResponse
import com.hazel.customers.dto.CustomerSearchResult
import com.hazel.customers.dto.CustomerUpdateRequest
import com.hazel.customers.dto.FindOrCreateCustomerRequest
import com.hazel.customers.dto.UpdateGradeRequest
import com.hazel.customers.service.CustomerService
import com.hazel.sales.dto.SalesPageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

@Tag(name = "Customers", description = "고객 관리")
@RestController
@RequestMapping("/customers")
class CustomerController(
    private val customerService: CustomerService,
) {
    @Operation(summary = "고객 목록", description = "구매 통계 포함, 총 구매액 내림차순")
    @GetMapping
    fun list(): List<CustomerResponse> = customerService.list()

    @Operation(summary = "고객 이름 검색")
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
    ): List<CustomerSearchResult> = customerService.searchByName(q)

    @Operation(summary = "전화번호 중복 확인", description = "중복이면 해당 고객, 아니면 204")
    @GetMapping("/check-phone")
    fun checkPhone(
        @RequestParam phone: String,
        @RequestParam(required = false) excludeId: UUID?,
    ): ResponseEntity<CustomerSearchResult> =
        customerService
            .checkPhoneDuplicate(phone, excludeId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()

    @Operation(summary = "고객 단건 조회", description = "구매 통계 포함")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): CustomerResponse = customerService.get(id)

    @Operation(summary = "고객별 매출 조회")
    @GetMapping("/{id}/sales")
    fun customerSales(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): SalesPageResponse = customerService.getCustomerSales(id, page, size)

    @Operation(summary = "고객 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CustomerCreateRequest,
    ): CustomerResponse = customerService.create(request)

    @Operation(summary = "고객 찾기/생성", description = "전화번호+계정 복합 키 기준")
    @PostMapping("/find-or-create")
    fun findOrCreate(
        @Valid @RequestBody request: FindOrCreateCustomerRequest,
    ): CustomerResponse = customerService.findOrCreate(requireNotNull(request.name), requireNotNull(request.phone))

    @Operation(summary = "고객 수정")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CustomerUpdateRequest,
    ): CustomerResponse = customerService.update(id, request)

    @Operation(summary = "고객 등급 변경")
    @PatchMapping("/{id}/grade")
    fun updateGrade(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateGradeRequest,
    ): CustomerResponse = customerService.updateGrade(id, requireNotNull(request.grade))

    @Operation(summary = "고객 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) {
        customerService.delete(id)
    }
}
