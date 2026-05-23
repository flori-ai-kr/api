package com.hazel.expenses.repository

import com.hazel.expenses.entity.RecurringSkip
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface RecurringSkipRepository : JpaRepository<RecurringSkip, UUID> {
    fun existsByRecurringIdAndSkipDate(
        recurringId: UUID,
        skipDate: LocalDate,
    ): Boolean

    fun findByRecurringIdInAndSkipDate(
        recurringIds: Collection<UUID>,
        skipDate: LocalDate,
    ): List<RecurringSkip>
}
