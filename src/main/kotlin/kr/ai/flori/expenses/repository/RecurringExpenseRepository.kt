package kr.ai.flori.expenses.repository

import kr.ai.flori.expenses.entity.RecurringExpense
import org.springframework.data.jpa.repository.JpaRepository

interface RecurringExpenseRepository : JpaRepository<RecurringExpense, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): RecurringExpense?

    fun findByUserIdOrderByIsActiveDescCreatedAtDesc(userId: Long): List<RecurringExpense>

    /** 자동생성 cron: 전체 테넌트의 활성 템플릿(시스템 작업). */
    fun findByIsActiveTrue(): List<RecurringExpense>
}
