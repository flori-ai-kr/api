package kr.ai.flori.expenses.service

import kr.ai.flori.expenses.entity.RecurringExpense
import kr.ai.flori.expenses.entity.YearlyDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecurringScheduleEvaluatorTest {
    private fun rule(
        frequency: String,
        startDate: LocalDate,
        intervalCount: Int = 1,
        endDate: LocalDate? = null,
        daysOfWeek: List<Int> = emptyList(),
        daysOfMonth: List<Int> = emptyList(),
        yearlyDates: List<YearlyDate> = emptyList(),
    ): RecurringExpense =
        RecurringExpense(1L, "임대료", "rent", 1000, 1, "transfer", frequency, startDate).apply {
            this.intervalCount = intervalCount
            this.endDate = endDate
            this.daysOfWeek = daysOfWeek
            this.daysOfMonth = daysOfMonth
            this.yearlyDates = yearlyDates
        }

    @Test
    fun `매주 — 지정 요일에만 발생`() {
        val r = rule("weekly", LocalDate.of(2026, 5, 25), daysOfWeek = listOf(1)) // 월요일
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 5, 25))).isTrue() // 월
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 5, 26))).isFalse() // 화
    }

    @Test
    fun `매주 격주 — 시작 기준 짝수 주에만 발생`() {
        val r = rule("weekly", LocalDate.of(2026, 5, 25), intervalCount = 2, daysOfWeek = listOf(1))
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 6, 1))).isFalse() // 1주차
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 6, 8))).isTrue() // 2주차
    }

    @Test
    fun `매월 — 지정일 + 말일 클램핑`() {
        val r = rule("monthly", LocalDate.of(2026, 5, 15), daysOfMonth = listOf(15))
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 6, 15))).isTrue()
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 6, 14))).isFalse()

        val endOfMonth = rule("monthly", LocalDate.of(2026, 1, 31), daysOfMonth = listOf(31))
        assertThat(RecurringScheduleEvaluator.isDue(endOfMonth, LocalDate.of(2026, 6, 30))).isTrue() // 6월 30일=말일
    }

    @Test
    fun `매년 — 지정 월일에만 발생`() {
        val r = rule("yearly", LocalDate.of(2026, 5, 22), yearlyDates = listOf(YearlyDate(5, 22)))
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2027, 5, 22))).isTrue()
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2027, 5, 23))).isFalse()
    }

    @Test
    fun `시작 전·종료 후는 발생하지 않는다`() {
        val r =
            rule(
                "monthly",
                LocalDate.of(2026, 5, 15),
                endDate = LocalDate.of(2026, 7, 31),
                daysOfMonth = listOf(15),
            )
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 4, 15))).isFalse() // 시작 전
        assertThat(RecurringScheduleEvaluator.isDue(r, LocalDate.of(2026, 8, 15))).isFalse() // 종료 후
    }
}
