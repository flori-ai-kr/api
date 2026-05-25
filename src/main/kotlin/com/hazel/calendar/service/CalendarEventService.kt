package com.hazel.calendar.service

import com.hazel.calendar.dto.CalendarEventCreateRequest
import com.hazel.calendar.dto.CalendarEventResponse
import com.hazel.calendar.dto.CalendarEventUpdateRequest
import com.hazel.calendar.entity.CalendarEvent
import com.hazel.calendar.repository.CalendarEventRepository
import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

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
    fun get(id: UUID): CalendarEventResponse = CalendarEventResponse.from(load(id))

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
        id: UUID,
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
    fun delete(id: UUID) {
        repository.delete(load(id))
    }

    private fun load(id: UUID): CalendarEvent =
        repository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "이벤트를 찾을 수 없습니다")

    private fun requireValidRange(
        start: LocalDate,
        end: LocalDate,
    ) {
        if (end.isBefore(start)) throw AppException(ErrorCode.VALIDATION, "종료일은 시작일보다 이전일 수 없습니다")
    }
}
