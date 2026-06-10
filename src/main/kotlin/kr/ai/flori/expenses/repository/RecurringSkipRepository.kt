package kr.ai.flori.expenses.repository

import kr.ai.flori.expenses.entity.RecurringSkip
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RecurringSkipRepository : JpaRepository<RecurringSkip, Long> {
    fun existsByRecurringIdAndSkipDate(
        recurringId: Long,
        skipDate: LocalDate,
    ): Boolean

    fun findByRecurringIdInAndSkipDate(
        recurringIds: Collection<Long>,
        skipDate: LocalDate,
    ): List<RecurringSkip>

    /** 고정비 템플릿 삭제 시 해당 템플릿의 skip 기록 제거(FK CASCADE 대체). */
    @Modifying
    @Query("DELETE FROM RecurringSkip s WHERE s.userId = :userId AND s.recurringId = :recurringId")
    fun deleteByUserIdAndRecurringId(
        @Param("userId") userId: Long,
        @Param("recurringId") recurringId: Long,
    ): Int
}
