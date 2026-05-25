package com.hazel.expenses.service

import com.hazel.common.util.KST
import com.hazel.expenses.entity.RecurringExpense
import com.hazel.expenses.repository.RecurringExpenseRepository
import com.hazel.expenses.repository.RecurringSkipRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Date
import java.time.LocalDate

/**
 * 고정비 자동생성. 매일 KST 00:30, 발생 대상(active·due·skip 제외) 템플릿으로 expenses를 멱등 생성.
 * (recurring_id, date) UNIQUE + ON CONFLICT DO NOTHING 으로 중복 생성 방지.
 *
 * 시스템 작업(전체 테넌트) — 각 행의 user_id는 템플릿에서 그대로 복사한다.
 */
@Service
class RecurringExpenseGenerator(
    private val recurringRepository: RecurringExpenseRepository,
    private val skipRepository: RecurringSkipRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    fun scheduledGenerate() {
        val today = LocalDate.now(KST)
        val count = generateForDate(today)
        log.info("고정비 자동생성 완료: date={} inserted={}", today, count)
    }

    fun generateForDate(date: LocalDate): Int {
        val due = recurringRepository.findByIsActiveTrue().filter { RecurringScheduleEvaluator.isDue(it, date) }
        if (due.isEmpty()) return 0

        val skipped =
            skipRepository
                .findByRecurringIdInAndSkipDate(due.mapNotNull { it.id }, date)
                .map { it.recurringId }
                .toSet()

        // 건별 격리: 한 템플릿 생성 실패가 나머지를 막지 않는다(메서드 @Transactional 미사용 = 건별 독립 커밋).
        var inserted = 0
        due.filter { it.id !in skipped }.forEach { rule ->
            try {
                inserted += insertExpense(rule, date)
            } catch (e: DataAccessException) {
                log.error("고정비 자동생성 실패 recurringId={}", rule.id, e)
            }
        }
        return inserted
    }

    private fun insertExpense(
        rule: RecurringExpense,
        date: LocalDate,
    ): Int =
        jdbcTemplate.update(
            "INSERT INTO expenses (user_id, date, item_name, category, unit_price, quantity, total_amount, " +
                "payment_method, vendor, note, recurring_id, is_recurring_modified) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE) " +
                "ON CONFLICT (recurring_id, date) DO NOTHING",
            rule.userId,
            Date.valueOf(date),
            rule.itemName,
            rule.category,
            rule.unitPrice,
            rule.quantity,
            rule.unitPrice * rule.quantity,
            rule.paymentMethod,
            rule.vendor,
            rule.note,
            rule.id,
        )
}
