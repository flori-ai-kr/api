package com.hazel.expenses.repository

import com.hazel.expenses.entity.RecurringExpense
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RecurringExpenseRepository : JpaRepository<RecurringExpense, UUID> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): RecurringExpense?

    fun findByUserIdOrderByIsActiveDescCreatedAtDesc(userId: UUID): List<RecurringExpense>

    /** 자동생성 cron: 전체 테넌트의 활성 템플릿(시스템 작업). */
    fun findByIsActiveTrue(): List<RecurringExpense>
}
