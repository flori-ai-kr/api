package com.hazel.reservations.service

import com.hazel.common.domain.ReservationStatuses
import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.common.util.KST
import com.hazel.reservations.dto.AddPickupRequest
import com.hazel.reservations.dto.ReservationCreateRequest
import com.hazel.reservations.dto.ReservationResponse
import com.hazel.reservations.dto.ReservationSuggestionsResponse
import com.hazel.reservations.dto.ReservationUpdateRequest
import com.hazel.reservations.entity.Reservation
import com.hazel.reservations.repository.ReservationRepository
import com.hazel.sales.dto.SaleCreateRequest
import com.hazel.sales.dto.SaleResponse
import com.hazel.sales.service.SaleService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 예약(픽업) 서비스. 모든 쿼리 TenantContext userId 격리(HARD).
 * 매출 전환/픽업 추가로 sales와 양방향 연결.
 */
@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val saleService: SaleService,
) {
    @Transactional(readOnly = true)
    fun listByMonth(month: String): List<ReservationResponse> {
        val ym = YearMonth.parse(month)
        return reservationRepository
            .findByUserIdAndDateBetweenOrderByDateAscTimeAsc(TenantContext.currentUserId(), ym.atDay(1), ym.atEndOfMonth())
            .map(ReservationResponse::from)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ReservationResponse = ReservationResponse.from(load(id))

    @Transactional(readOnly = true)
    fun upcoming(): List<ReservationResponse> =
        reservationRepository
            .findByUserIdAndStatusNotAndDateGreaterThanEqualOrderByDateAscTimeAsc(
                TenantContext.currentUserId(),
                ReservationStatuses.CANCELLED,
                LocalDate.now(KST),
            ).map(ReservationResponse::from)

    @Transactional(readOnly = true)
    fun triggeredReminders(): List<ReservationResponse> {
        val now = Instant.now()
        return reservationRepository
            .findTriggeredReminders(TenantContext.currentUserId(), now, now.minus(REMINDER_WINDOW))
            .map(ReservationResponse::from)
    }

    @Transactional(readOnly = true)
    fun forSale(saleId: UUID): List<ReservationResponse> =
        reservationRepository
            .findByUserIdAndSaleIdOrderByDateAsc(TenantContext.currentUserId(), saleId)
            .map(ReservationResponse::from)

    @Transactional(readOnly = true)
    fun suggestions(): ReservationSuggestionsResponse {
        val userId = TenantContext.currentUserId()
        return ReservationSuggestionsResponse(
            titles = reservationRepository.findTitlesByFrequency(userId),
            descriptions = reservationRepository.findDescriptionsByFrequency(userId),
        )
    }

    @Transactional
    fun create(request: ReservationCreateRequest): ReservationResponse {
        val reservation = Reservation(TenantContext.currentUserId(), requireNotNull(request.date))
        reservation.time = request.time
        reservation.customerName = requireNotNull(request.customerName)
        reservation.customerPhone = request.customerPhone
        reservation.title = requireNotNull(request.title)
        reservation.description = request.description
        reservation.amount = request.amount
        reservation.status = validStatus(request.status ?: ReservationStatuses.PENDING)
        reservation.reminderAt = request.reminderAt
        return ReservationResponse.from(reservationRepository.save(reservation))
    }

    @Transactional
    fun update(
        id: UUID,
        request: ReservationUpdateRequest,
    ): ReservationResponse {
        val reservation = load(id)
        request.date?.let { reservation.date = it }
        request.time?.let { reservation.time = it }
        request.customerName?.let { reservation.customerName = it }
        request.customerPhone?.let { reservation.customerPhone = it }
        request.title?.let { reservation.title = it }
        request.description?.let { reservation.description = it }
        request.amount?.let { reservation.amount = it }
        request.status?.let { reservation.status = validStatus(it) }
        request.saleId?.let {
            saleService.get(it) // 소유권 검증(타 테넌트 매출 연결 차단) — 미존재/타인 시 NOT_FOUND
            reservation.saleId = it
        }
        request.pickupCompleted?.let { reservation.pickupCompleted = it }
        request.reminderAt?.let {
            reservation.reminderAt = it
            reservation.reminderSent = false // 리마인더 변경 시 재발송 가능하도록 리셋
        }
        reservation.updatedAt = Instant.now()
        return ReservationResponse.from(reservationRepository.save(reservation))
    }

    @Transactional
    fun markPickupCompleted(
        id: UUID,
        completed: Boolean,
    ): ReservationResponse {
        val reservation = load(id)
        reservation.pickupCompleted = completed
        reservation.updatedAt = Instant.now()
        return ReservationResponse.from(reservationRepository.save(reservation))
    }

    @Transactional
    fun delete(id: UUID) {
        reservationRepository.delete(load(id))
    }

    /** 예약 → 매출 전환: 매출 생성 후 예약에 sale_id 연결. */
    @Transactional
    fun convertToSale(
        reservationId: UUID,
        saleRequest: SaleCreateRequest,
    ): SaleResponse {
        val reservation = load(reservationId)
        val sale = saleService.create(saleRequest)
        reservation.saleId = sale.id
        reservation.updatedAt = Instant.now()
        reservationRepository.save(reservation)
        return sale
    }

    /** 기존 매출에 픽업(예약) 추가: 고객 정보는 매출에서 상속. */
    @Transactional
    fun addPickupToSale(
        saleId: UUID,
        request: AddPickupRequest,
    ): ReservationResponse {
        val userId = TenantContext.currentUserId()
        val sale = saleService.get(saleId) // 소유권 검증 + 고객정보 상속(SaleService 경유)
        val reservation = Reservation(userId, requireNotNull(request.date))
        reservation.time = request.time
        reservation.customerName = sale.customerName ?: ""
        reservation.customerPhone = sale.customerPhone
        reservation.title = requireNotNull(request.title)
        reservation.amount = request.amount
        reservation.saleId = saleId
        reservation.reminderAt = request.reminderAt
        return ReservationResponse.from(reservationRepository.save(reservation))
    }

    private fun load(id: UUID): Reservation =
        reservationRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "예약을 찾을 수 없습니다")

    private fun validStatus(value: String): String {
        if (value !in ReservationStatuses.ALL) throw AppException(ErrorCode.VALIDATION, "올바르지 않은 상태입니다")
        return value
    }

    private companion object {
        val REMINDER_WINDOW: Duration = Duration.ofHours(48)
    }
}
