package kr.ai.flori.expenses.repository

import kr.ai.flori.expenses.entity.RecurringSkip
import org.springframework.data.jpa.repository.JpaRepository
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
}
