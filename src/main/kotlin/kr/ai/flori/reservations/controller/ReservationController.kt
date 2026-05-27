package kr.ai.flori.reservations.controller

import jakarta.validation.Valid
import kr.ai.flori.reservations.dto.*
import kr.ai.flori.reservations.service.ReservationService
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.dto.SaleResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/reservations")
class ReservationController(
    private val reservationService: ReservationService,
) {
    @GetMapping
    fun list(
        @RequestParam month: String,
    ): List<ReservationResponse> = reservationService.listByMonth(month)

    @GetMapping("/upcoming")
    fun upcoming(): List<ReservationResponse> = reservationService.upcoming()

    @GetMapping("/reminders")
    fun reminders(): List<ReservationResponse> = reservationService.triggeredReminders()

    @GetMapping("/suggestions")
    fun suggestions(): ReservationSuggestionsResponse = reservationService.suggestions()

    @GetMapping("/by-sale/{saleId}")
    fun bySale(
        @PathVariable saleId: Long,
    ): List<ReservationResponse> = reservationService.forSale(saleId)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ReservationResponse = reservationService.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ReservationCreateRequest,
    ): ReservationResponse = reservationService.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReservationUpdateRequest,
    ): ReservationResponse = reservationService.update(id, request)

    @PostMapping("/{id}/complete-pickup")
    fun completePickup(
        @PathVariable id: Long,
        @Valid @RequestBody request: PickupCompleteRequest,
    ): ReservationResponse = reservationService.markPickupCompleted(id, requireNotNull(request.completed))

    @PostMapping("/{id}/convert-to-sale")
    @ResponseStatus(HttpStatus.CREATED)
    fun convertToSale(
        @PathVariable id: Long,
        @Valid @RequestBody request: SaleCreateRequest,
    ): SaleResponse = reservationService.convertToSale(id, request)

    @PostMapping("/add-pickup/{saleId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun addPickup(
        @PathVariable saleId: Long,
        @Valid @RequestBody request: AddPickupRequest,
    ): ReservationResponse = reservationService.addPickupToSale(saleId, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        reservationService.delete(id)
    }
}
