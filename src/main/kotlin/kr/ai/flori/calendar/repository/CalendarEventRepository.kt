package kr.ai.flori.calendar.repository

import kr.ai.flori.calendar.entity.CalendarEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface CalendarEventRepository : JpaRepository<CalendarEvent, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): CalendarEvent?

    /** 월 범위와 겹치는 이벤트: start <= 월말 AND end >= 월초. */
    @Query(
        "SELECT e FROM CalendarEvent e WHERE e.userId = :userId " +
            "AND e.startDate <= :end AND e.endDate >= :start ORDER BY e.startDate, e.id",
    )
    fun findOverlapping(
        @Param("userId") userId: Long,
        @Param("start") start: LocalDate,
        @Param("end") end: LocalDate,
    ): List<CalendarEvent>
}
