package kr.ai.flori.expenses.service

import kr.ai.flori.expenses.entity.RecurringExpense
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 고정비 템플릿이 특정 날짜에 발생(자동생성 대상)하는지 판정하는 순수 로직.
 * 원본 cron의 isDueToday 규칙을 이식. 외부 의존 없이 단위 테스트 가능.
 */
object RecurringScheduleEvaluator {
    private const val DAYS_PER_WEEK = 7
    private const val MONTHS_PER_YEAR = 12

    fun isDue(
        rule: RecurringExpense,
        target: LocalDate,
    ): Boolean {
        if (target.isBefore(rule.startDate)) return false
        rule.endDate?.let { if (target.isAfter(it)) return false }
        val interval = rule.intervalCount.coerceAtLeast(1)
        return when (rule.frequency) {
            "weekly" -> isWeeklyDue(rule, target, interval)
            "monthly" -> isMonthlyDue(rule, target, interval)
            "yearly" -> isYearlyDue(rule, target, interval)
            else -> false
        }
    }

    private fun isWeeklyDue(
        rule: RecurringExpense,
        target: LocalDate,
        interval: Int,
    ): Boolean {
        // ISO(월=1..일=7) → 원본 규약(일=0..토=6)
        val dow = target.dayOfWeek.value % DAYS_PER_WEEK
        if (dow !in rule.daysOfWeek) return false
        val weeks = ChronoUnit.DAYS.between(rule.startDate, target) / DAYS_PER_WEEK
        return weeks % interval == 0L
    }

    private fun isMonthlyDue(
        rule: RecurringExpense,
        target: LocalDate,
        interval: Int,
    ): Boolean {
        val lastDay = target.lengthOfMonth()
        val matches = rule.daysOfMonth.any { minOf(it, lastDay) == target.dayOfMonth }
        if (!matches) return false
        val months = (target.year - rule.startDate.year) * MONTHS_PER_YEAR + (target.monthValue - rule.startDate.monthValue)
        return months >= 0 && months % interval == 0
    }

    private fun isYearlyDue(
        rule: RecurringExpense,
        target: LocalDate,
        interval: Int,
    ): Boolean {
        val lastDay = target.lengthOfMonth()
        val matches = rule.yearlyDates.any { it.m == target.monthValue && minOf(it.d, lastDay) == target.dayOfMonth }
        if (!matches) return false
        val years = target.year - rule.startDate.year
        return years >= 0 && years % interval == 0
    }
}
