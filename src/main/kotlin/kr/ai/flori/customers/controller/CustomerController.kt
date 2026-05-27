package kr.ai.flori.customers.controller

import jakarta.validation.Valid
import kr.ai.flori.customers.dto.CustomerCreateRequest
import kr.ai.flori.customers.dto.CustomerResponse
import kr.ai.flori.customers.dto.CustomerSearchResult
import kr.ai.flori.customers.dto.CustomerUpdateRequest
import kr.ai.flori.customers.dto.FindOrCreateCustomerRequest
import kr.ai.flori.customers.dto.UpdateGradeRequest
import kr.ai.flori.customers.service.CustomerService
import kr.ai.flori.sales.dto.SalesPageResponse
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

@RestController
@RequestMapping("/customers")
class CustomerController(
    private val customerService: CustomerService,
) {
    @GetMapping
    fun list(): List<CustomerResponse> = customerService.list()

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
    ): List<CustomerSearchResult> = customerService.searchByName(q)

    @GetMapping("/check-phone")
    fun checkPhone(
        @RequestParam phone: String,
        @RequestParam(required = false) excludeId: Long?,
    ): ResponseEntity<CustomerSearchResult> =
        customerService
            .checkPhoneDuplicate(phone, excludeId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): CustomerResponse = customerService.get(id)

    @GetMapping("/{id}/sales")
    fun customerSales(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): SalesPageResponse = customerService.getCustomerSales(id, page, size)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CustomerCreateRequest,
    ): CustomerResponse = customerService.create(request)

    @PostMapping("/find-or-create")
    fun findOrCreate(
        @Valid @RequestBody request: FindOrCreateCustomerRequest,
    ): CustomerResponse = customerService.findOrCreate(requireNotNull(request.name), requireNotNull(request.phone))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: CustomerUpdateRequest,
    ): CustomerResponse = customerService.update(id, request)

    @PatchMapping("/{id}/grade")
    fun updateGrade(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateGradeRequest,
    ): CustomerResponse = customerService.updateGrade(id, requireNotNull(request.grade))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        customerService.delete(id)
    }
}
