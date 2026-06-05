package kr.ai.flori.reservations.service

import kr.ai.flori.common.domain.ReservationStatuses
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.KST
import kr.ai.flori.customers.service.CustomerService
import kr.ai.flori.reservations.dto.AddPickupRequest
import kr.ai.flori.reservations.dto.ReservationCreateRequest
import kr.ai.flori.reservations.dto.ReservationResponse
import kr.ai.flori.reservations.dto.ReservationSuggestionsResponse
import kr.ai.flori.reservations.dto.ReservationUpdateRequest
import kr.ai.flori.reservations.entity.Reservation
import kr.ai.flori.reservations.repository.ReservationRepository
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.dto.SaleResponse
import kr.ai.flori.sales.repository.SaleRepository
import kr.ai.flori.sales.service.SaleService
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 예약(픽업) 서비스. 모든 쿼리 TenantContext userId 격리(HARD).
 * 매출 전환/픽업 추가로 sales와 양방향 연결.
 */
@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val saleService: SaleService,
    private val saleRepository: SaleRepository,
    private val customerService: CustomerService,
    private val labelReader: LabelSettingReader,
) {
    @Transactional(readOnly = true)
    fun listByMonth(month: String): List<ReservationResponse> {
        val userId = TenantContext.currentUserId()
        val ym = YearMonth.parse(month)
        val reservations =
            reservationRepository
                .findByUserIdAndDateBetweenOrderByDateAscTimeAsc(userId, ym.atDay(1), ym.atEndOfMonth())

        // 매출 조인 enrichment: 연결된 매출/고객 구매횟수를 일괄 조회해 카드 표시 필드를 채운다.
        val saleIds = reservations.mapNotNull { it.saleId }.distinct()
        val salesById =
            if (saleIds.isEmpty()) {
                emptyMap()
            } else {
                saleRepository.findAllById(saleIds).filter { it.userId == userId }.associateBy { it.id }
            }
        val purchaseCounts = customerService.purchaseCountsByCustomer()
        val catMap = labelReader.labelMap(LabelDomains.SALE, LabelKinds.CATEGORY)
        val chMap = labelReader.labelMap(LabelDomains.SALE, LabelKinds.CHANNEL)

        return reservations.map { r ->
            val sale = r.saleId?.let { salesById[it] }
            val purchaseCount = sale?.customerId?.let { purchaseCounts[it] }
            ReservationResponse.from(
                r,
                sale,
                purchaseCount,
                sale?.categoryId?.let { catMap[it] },
                sale?.channelId?.let { chMap[it] },
            )
        }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): ReservationResponse = ReservationResponse.from(load(id))

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
    fun forSale(saleId: Long): List<ReservationResponse> =
        reservationRepository
            .findByUserIdAndSaleIdOrderByDateAsc(TenantContext.currentUserId(), saleId)
            .map(ReservationResponse::from)

    @Transactional(readOnly = true)
    fun suggestions(): ReservationSuggestionsResponse {
        val userId = TenantContext.currentUserId()
        return ReservationSuggestionsResponse(
            titles = reservationRepository.findTitlesByFrequency(userId),
            memos = reservationRepository.findMemosByFrequency(userId),
        )
    }

    @Transactional
    fun create(request: ReservationCreateRequest): ReservationResponse {
        val customerName = requireNotNull(request.customerName)
        val reservation = Reservation(TenantContext.currentUserId(), requireNotNull(request.date))
        reservation.time = request.time
        reservation.customerName = customerName
        reservation.customerPhone = request.customerPhone
        reservation.title = requireNotNull(request.title)
        reservation.memo = request.memo
        reservation.amount = request.amount
        reservation.status = validStatus(request.status ?: ReservationStatuses.PENDING)
        reservation.reminderAt = request.reminderAt

        request.customerPhone?.takeIf { it.isNotBlank() }?.let { phone ->
            customerService.findOrCreate(customerName, phone)
        }

        return ReservationResponse.from(reservationRepository.save(reservation))
    }

    @Transactional
    fun update(
        id: Long,
        request: ReservationUpdateRequest,
    ): ReservationResponse {
        val reservation = load(id)
        request.date?.let { reservation.date = it }
        request.time?.let { reservation.time = it }
        request.customerName?.let { reservation.customerName = it }
        request.customerPhone?.let { reservation.customerPhone = it }
        request.title?.let { reservation.title = it }
        request.memo?.let { reservation.memo = it }
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
        return ReservationResponse.from(reservationRepository.save(reservation))
    }

    @Transactional
    fun markPickupCompleted(
        id: Long,
        completed: Boolean,
    ): ReservationResponse {
        val reservation = load(id)
        reservation.pickupCompleted = completed
        return ReservationResponse.from(reservationRepository.save(reservation))
    }

    @Transactional
    fun delete(id: Long) {
        reservationRepository.delete(load(id))
    }

    /** 예약 → 매출 전환: 매출 생성 후 예약에 sale_id 연결. */
    @Transactional
    fun convertToSale(
        reservationId: Long,
        saleRequest: SaleCreateRequest,
    ): SaleResponse {
        val reservation = load(reservationId)
        val sale = saleService.create(saleRequest)
        reservation.saleId = sale.id
        reservationRepository.save(reservation)
        return sale
    }

    /** 기존 매출에 픽업(예약) 추가: 고객 정보는 매출에서 상속. */
    @Transactional
    fun addPickupToSale(
        saleId: Long,
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

    private fun load(id: Long): Reservation =
        reservationRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "예약을 찾을 수 없습니다")

    private fun validStatus(value: String): String {
        if (value !in ReservationStatuses.ALL) throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 상태입니다")
        return value
    }

    private companion object {
        val REMINDER_WINDOW: Duration = Duration.ofHours(48)
    }
}
