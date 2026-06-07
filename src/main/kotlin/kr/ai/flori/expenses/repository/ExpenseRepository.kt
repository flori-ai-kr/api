package kr.ai.flori.expenses.repository

import kr.ai.flori.expenses.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ExpenseRepository :
    JpaRepository<Expense, Long>,
    JpaSpecificationExecutor<Expense> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Expense?

    fun findByUserIdOrderByDateDesc(userId: Long): List<Expense>

    fun findByUserIdAndDateBetweenOrderByDateDesc(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<Expense>

    @Query(
        "SELECT e.itemName FROM Expense e WHERE e.userId = :userId AND e.itemName <> '' " +
            "GROUP BY e.itemName ORDER BY COUNT(e.itemName) DESC",
    )
    fun findItemNamesByFrequency(
        @Param("userId") userId: Long,
    ): List<String>

    @Query(
        "SELECT e.vendor FROM Expense e WHERE e.userId = :userId AND e.vendor IS NOT NULL AND e.vendor <> '' " +
            "GROUP BY e.vendor ORDER BY COUNT(e.vendor) DESC",
    )
    fun findVendorsByFrequency(
        @Param("userId") userId: Long,
    ): List<String>

    @Query(
        "SELECT e.memo FROM Expense e WHERE e.userId = :userId AND e.memo IS NOT NULL AND e.memo <> '' " +
            "GROUP BY e.memo ORDER BY COUNT(e.memo) DESC",
    )
    fun findMemosByFrequency(
        @Param("userId") userId: Long,
    ): List<String>

    /** 고정비 템플릿 삭제 시 자동생성된 지출의 recurring_id를 NULL로(지출 자체는 보존). */
    @Modifying
    @Query("UPDATE Expense e SET e.recurringId = null WHERE e.userId = :userId AND e.recurringId = :recurringId")
    fun clearRecurringReference(
        @Param("userId") userId: Long,
        @Param("recurringId") recurringId: Long,
    ): Int
}
