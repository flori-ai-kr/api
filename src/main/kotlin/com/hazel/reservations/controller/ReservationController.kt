package com.hazel.reservations.controller

import com.hazel.reservations.dto.AddPickupRequest
import com.hazel.reservations.dto.PickupCompleteRequest
import com.hazel.reservations.dto.ReservationCreateRequest
import com.hazel.reservations.dto.ReservationResponse
import com.hazel.reservations.dto.ReservationSuggestionsResponse
import com.hazel.reservations.dto.ReservationUpdateRequest
import com.hazel.reservations.service.ReservationService
import com.hazel.sales.dto.SaleCreateRequest
import com.hazel.sales.dto.SaleResponse
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

@Tag(name = "Reservations", description = "예약/픽업 관리")
@RestController
@RequestMapping("/reservations")
class ReservationController(
    private val reservationService: ReservationService,
) {
    @Operation(summary = "월별 예약 목록")
    @GetMapping
    fun list(
        @RequestParam month: String,
    ): List<ReservationResponse> = reservationService.listByMonth(month)

    @Operation(summary = "다가오는 예약")
    @GetMapping("/upcoming")
    fun upcoming(): List<ReservationResponse> = reservationService.upcoming()

    @Operation(summary = "발동된 리마인더", description = "최근 48시간 내 도달")
    @GetMapping("/reminders")
    fun reminders(): List<ReservationResponse> = reservationService.triggeredReminders()

    @Operation(summary = "예약 제목/메모 자동완성")
    @GetMapping("/suggestions")
    fun suggestions(): ReservationSuggestionsResponse = reservationService.suggestions()

    @Operation(summary = "매출 연결 예약 목록")
    @GetMapping("/by-sale/{saleId}")
    fun bySale(
        @PathVariable saleId: UUID,
    ): List<ReservationResponse> = reservationService.forSale(saleId)

    @Operation(summary = "예약 단건 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ReservationResponse = reservationService.get(id)

    @Operation(summary = "예약 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ReservationCreateRequest,
    ): ReservationResponse = reservationService.create(request)

    @Operation(summary = "예약 수정", description = "reminder_at 변경 시 재발송 가능하도록 리셋")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReservationUpdateRequest,
    ): ReservationResponse = reservationService.update(id, request)

    @Operation(summary = "픽업 완료 처리")
    @PostMapping("/{id}/complete-pickup")
    fun completePickup(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PickupCompleteRequest,
    ): ReservationResponse = reservationService.markPickupCompleted(id, requireNotNull(request.completed))

    @Operation(summary = "예약 → 매출 전환", description = "매출 생성 후 예약에 연결")
    @PostMapping("/{id}/convert-to-sale")
    @ResponseStatus(HttpStatus.CREATED)
    fun convertToSale(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SaleCreateRequest,
    ): SaleResponse = reservationService.convertToSale(id, request)

    @Operation(summary = "매출에 픽업 추가", description = "고객 정보는 매출에서 상속")
    @PostMapping("/add-pickup/{saleId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun addPickup(
        @PathVariable saleId: UUID,
        @Valid @RequestBody request: AddPickupRequest,
    ): ReservationResponse = reservationService.addPickupToSale(saleId, request)

    @Operation(summary = "예약 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) {
        reservationService.delete(id)
    }
}
