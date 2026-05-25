package kr.ai.flori.expenses.repository

import kr.ai.flori.expenses.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface ExpenseRepository : JpaRepository<Expense, UUID> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Expense?

    fun findByUserIdOrderByDateDesc(userId: UUID): List<Expense>

    fun findByUserIdAndDateBetweenOrderByDateDesc(
        userId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): List<Expense>

    @Query(
        "SELECT e.itemName FROM Expense e WHERE e.userId = :userId AND e.itemName <> '' " +
            "GROUP BY e.itemName ORDER BY COUNT(e.itemName) DESC",
    )
    fun findItemNamesByFrequency(
        @Param("userId") userId: UUID,
    ): List<String>

    @Query(
        "SELECT e.vendor FROM Expense e WHERE e.userId = :userId AND e.vendor IS NOT NULL AND e.vendor <> '' " +
            "GROUP BY e.vendor ORDER BY COUNT(e.vendor) DESC",
    )
    fun findVendorsByFrequency(
        @Param("userId") userId: UUID,
    ): List<String>

    @Query(
        "SELECT e.note FROM Expense e WHERE e.userId = :userId AND e.note IS NOT NULL AND e.note <> '' " +
            "GROUP BY e.note ORDER BY COUNT(e.note) DESC",
    )
    fun findNotesByFrequency(
        @Param("userId") userId: UUID,
    ): List<String>
}
