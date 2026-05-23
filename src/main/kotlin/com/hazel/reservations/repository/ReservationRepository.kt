package com.hazel.reservations.repository

import com.hazel.reservations.entity.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Reservation?

    fun findByUserIdAndDateBetweenOrderByDateAscTimeAsc(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): List<Reservation>

    fun findByUserIdAndSaleIdOrderByDateAsc(
        userId: UUID,
        saleId: UUID,
    ): List<Reservation>

    fun findByUserIdAndStatusNotAndDateGreaterThanEqualOrderByDateAscTimeAsc(
        userId: UUID,
        status: String,
        date: LocalDate,
    ): List<Reservation>

    /** 발동된 리마인더(읽기 API): 최근 48시간 윈도 내, 취소 제외. */
    @Query(
        "SELECT r FROM Reservation r WHERE r.userId = :userId AND r.status <> 'cancelled' " +
            "AND r.reminderAt IS NOT NULL AND r.reminderAt <= :now AND r.reminderAt >= :cutoff " +
            "ORDER BY r.reminderAt DESC",
    )
    fun findTriggeredReminders(
        @Param("userId") userId: UUID,
        @Param("now") now: Instant,
        @Param("cutoff") cutoff: Instant,
    ): List<Reservation>

    /** 발송 대상 리마인더(스케줄러, 전체 테넌트): 미발송·도달·취소 제외. */
    @Query(
        "SELECT r FROM Reservation r WHERE r.status <> 'cancelled' AND r.reminderSent = false " +
            "AND r.reminderAt IS NOT NULL AND r.reminderAt <= :now",
    )
    fun findDueReminders(
        @Param("now") now: Instant,
    ): List<Reservation>

    /** 일일 요약(스케줄러, 전체 테넌트): 해당 날짜의 비취소 예약. */
    fun findByDateAndStatusNot(
        date: LocalDate,
        status: String,
    ): List<Reservation>

    @Query(
        "SELECT r.title FROM Reservation r WHERE r.userId = :userId AND r.title <> '' " +
            "GROUP BY r.title ORDER BY COUNT(r.title) DESC",
    )
    fun findTitlesByFrequency(
        @Param("userId") userId: UUID,
    ): List<String>

    @Query(
        "SELECT r.description FROM Reservation r WHERE r.userId = :userId " +
            "AND r.description IS NOT NULL AND r.description <> '' " +
            "GROUP BY r.description ORDER BY COUNT(r.description) DESC",
    )
    fun findDescriptionsByFrequency(
        @Param("userId") userId: UUID,
    ): List<String>
}
