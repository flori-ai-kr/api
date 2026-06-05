package kr.ai.flori.schedules.repository

import kr.ai.flori.schedules.entity.Schedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ScheduleRepository : JpaRepository<Schedule, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Schedule?

    @Query(
        "SELECT s FROM Schedule s WHERE s.userId = :userId " +
            "AND s.startDate <= :end AND s.endDate >= :start ORDER BY s.startDate, s.id",
    )
    fun findOverlapping(
        @Param("userId") userId: Long,
        @Param("start") start: LocalDate,
        @Param("end") end: LocalDate,
    ): List<Schedule>
}
