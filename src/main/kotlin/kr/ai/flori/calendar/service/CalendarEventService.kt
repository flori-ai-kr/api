package kr.ai.flori.calendar.service

import kr.ai.flori.calendar.dto.CalendarEventCreateRequest
import kr.ai.flori.calendar.dto.CalendarEventResponse
import kr.ai.flori.calendar.dto.CalendarEventUpdateRequest
import kr.ai.flori.calendar.entity.CalendarEvent
import kr.ai.flori.calendar.repository.CalendarEventRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

/**
 * 캘린더 이벤트 서비스. 모든 쿼리 TenantContext userId 격리(HARD).
 */
@Service
class CalendarEventService(
    private val repository: CalendarEventRepository,
) {
    @Transactional(readOnly = true)
    fun listByMonth(month: String): List<CalendarEventResponse> {
        val ym = YearMonth.parse(month)
        return repository
            .findOverlapping(TenantContext.currentUserId(), ym.atDay(1), ym.atEndOfMonth())
            .map(CalendarEventResponse::from)
    }

    @Transactional(readOnly = true)
    fun get(id: Long): CalendarEventResponse = CalendarEventResponse.from(load(id))

    @Transactional
    fun create(request: CalendarEventCreateRequest): CalendarEventResponse {
        val start = requireNotNull(request.startDate)
        val end = requireNotNull(request.endDate)
        requireValidRange(start, end)
        val event = CalendarEvent(TenantContext.currentUserId(), requireNotNull(request.title), start, end)
        request.color?.let { event.color = it }
        event.description = request.description
        return CalendarEventResponse.from(repository.save(event))
    }

    @Transactional
    fun update(
        id: Long,
        request: CalendarEventUpdateRequest,
    ): CalendarEventResponse {
        val event = load(id)
        request.title?.let { event.title = it }
        request.startDate?.let { event.startDate = it }
        request.endDate?.let { event.endDate = it }
        request.color?.let { event.color = it }
        request.description?.let { event.description = it }
        requireValidRange(event.startDate, event.endDate)
        return CalendarEventResponse.from(repository.save(event))
    }

    @Transactional
    fun delete(id: Long) {
        repository.delete(load(id))
    }

    private fun load(id: Long): CalendarEvent =
        repository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "이벤트를 찾을 수 없습니다")

    private fun requireValidRange(
        start: LocalDate,
        end: LocalDate,
    ) {
        if (end.isBefore(start)) throw AppException(ErrorCode.VALIDATION, "종료일은 시작일보다 이전일 수 없습니다")
    }
}
