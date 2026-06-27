package kr.ai.flori.reservations.repository

import kr.ai.flori.reservations.entity.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate

interface ReservationRepository : JpaRepository<Reservation, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Reservation?

    fun findByUserIdAndDateBetweenOrderByDateAscTimeAsc(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<Reservation>

    fun findByUserIdAndSaleIdOrderByDateAsc(
        userId: Long,
        saleId: Long,
    ): List<Reservation>

    fun findByUserIdAndStatusNotAndDateGreaterThanEqualOrderByDateAscTimeAsc(
        userId: Long,
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
        @Param("userId") userId: Long,
        @Param("now") now: Instant,
        @Param("cutoff") cutoff: Instant,
    ): List<Reservation>

    /** 발송 대상 리마인더(스케줄러, 전체 테넌트): 미발송·도달, 취소·픽업완료(completed) 제외. */
    @Query(
        "SELECT r FROM Reservation r WHERE r.status NOT IN ('cancelled', 'completed') AND r.reminderSent = false " +
            "AND r.reminderAt IS NOT NULL AND r.reminderAt <= :now",
    )
    fun findDueReminders(
        @Param("now") now: Instant,
    ): List<Reservation>

    /** 일일 요약(스케줄러, 전체 테넌트): 해당 날짜 예약 중 취소·픽업완료(completed) 제외. */
    fun findByDateAndStatusNotIn(
        date: LocalDate,
        statuses: Collection<String>,
    ): List<Reservation>

    @Query(
        "SELECT r.title FROM Reservation r WHERE r.userId = :userId AND r.title <> '' " +
            "GROUP BY r.title ORDER BY COUNT(r.title) DESC",
    )
    fun findTitlesByFrequency(
        @Param("userId") userId: Long,
    ): List<String>

    @Query(
        "SELECT r.memo FROM Reservation r WHERE r.userId = :userId " +
            "AND r.memo IS NOT NULL AND r.memo <> '' " +
            "GROUP BY r.memo ORDER BY COUNT(r.memo) DESC",
    )
    fun findMemosByFrequency(
        @Param("userId") userId: Long,
    ): List<String>

    /** 매출 삭제 시 해당 매출에 연결된 예약의 sale_id를 NULL로(예약 자체는 보존). */
    @Modifying
    @Query("UPDATE Reservation r SET r.saleId = null WHERE r.userId = :userId AND r.saleId = :saleId")
    fun clearSaleReference(
        @Param("userId") userId: Long,
        @Param("saleId") saleId: Long,
    ): Int
}
