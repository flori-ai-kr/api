package kr.ai.flori.schedules.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.schedules.dto.ScheduleCreateRequest
import kr.ai.flori.schedules.dto.ScheduleResponse
import kr.ai.flori.schedules.dto.ScheduleUpdateRequest
import kr.ai.flori.schedules.entity.Schedule
import kr.ai.flori.schedules.repository.ScheduleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

@Service
class ScheduleService(
    private val repository: ScheduleRepository,
) {
    @Transactional(readOnly = true)
    fun listByMonth(month: String): List<ScheduleResponse> {
        val ym = YearMonth.parse(month)
        return repository
            .findOverlapping(TenantContext.currentUserId(), ym.atDay(1), ym.atEndOfMonth())
            .map(ScheduleResponse::from)
    }

    @Transactional(readOnly = true)
    fun get(id: Long): ScheduleResponse = ScheduleResponse.from(load(id))

    @Transactional
    fun create(request: ScheduleCreateRequest): ScheduleResponse {
        val start = requireNotNull(request.startDate)
        val end = requireNotNull(request.endDate)
        requireValidRange(start, end)
        val schedule = Schedule(TenantContext.currentUserId(), requireNotNull(request.title), start, end)
        request.color?.let { schedule.color = it }
        schedule.memo = request.memo
        return ScheduleResponse.from(repository.save(schedule))
    }

    @Transactional
    fun update(
        id: Long,
        request: ScheduleUpdateRequest,
    ): ScheduleResponse {
        val schedule = load(id)
        request.title?.let { schedule.title = it }
        request.startDate?.let { schedule.startDate = it }
        request.endDate?.let { schedule.endDate = it }
        request.color?.let { schedule.color = it }
        request.memo?.let { schedule.memo = it }
        requireValidRange(schedule.startDate, schedule.endDate)
        return ScheduleResponse.from(repository.save(schedule))
    }

    @Transactional
    fun delete(id: Long) {
        repository.delete(load(id))
    }

    private fun load(id: Long): Schedule =
        repository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다")

    private fun requireValidRange(
        start: LocalDate,
        end: LocalDate,
    ) {
        if (end.isBefore(start)) throw AppException(CommonErrorCode.VALIDATION, "종료일은 시작일보다 이전일 수 없습니다")
    }
}
