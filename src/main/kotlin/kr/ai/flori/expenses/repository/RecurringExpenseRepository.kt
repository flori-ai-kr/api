package kr.ai.flori.expenses.repository

import kr.ai.flori.expenses.entity.RecurringExpense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RecurringExpenseRepository : JpaRepository<RecurringExpense, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): RecurringExpense?

    fun findByUserIdOrderByIsActiveDescCreatedAtDesc(userId: Long): List<RecurringExpense>

    /**
     * 자동생성 cron: 전체 테넌트의 활성 + 해당 날짜의 유효기간(start_date~end_date) 내 템플릿(시스템 작업).
     * 발생 빈도(weekly/monthly/yearly) 판정은 [RecurringScheduleEvaluator]가 메모리에서 수행하므로,
     * DB에선 active·유효기간으로만 1차 좁혀 로드량을 줄인다.
     */
    @Query(
        "SELECT r FROM RecurringExpense r WHERE r.isActive = true " +
            "AND r.startDate <= :date AND (r.endDate IS NULL OR r.endDate >= :date)",
    )
    fun findActiveDueCandidates(
        @Param("date") date: LocalDate,
    ): List<RecurringExpense>
}
