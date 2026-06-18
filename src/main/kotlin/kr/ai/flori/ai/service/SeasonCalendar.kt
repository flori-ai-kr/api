package kr.ai.flori.ai.service

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/**
 * 한국 꽃 기념일 달력(결정적). 오늘 기준 향후 [HORIZON_DAYS]일 내 가장 가까운 기념일을 "이름 (D-N)"으로 돌려준다.
 * LLM 도구콜 없이 코드로 store_context.upcoming_season을 채우기 위한 결정적 계산기.
 *
 * - "기간형"(졸업·입학 시즌) 기념일은 해당 기간 시작일을 기준일로 삼는다(D-day는 시작까지).
 * - 연말→연초 경계는 다음 해 후보까지 포함해 가장 가까운 것을 고른다.
 */
@Component
class SeasonCalendar {
    /** [today] 기준 향후 [HORIZON_DAYS]일 내 가장 가까운 기념일 라벨("이름 (D-N)"). 없으면 null. */
    fun upcoming(today: LocalDate): String? {
        val candidate =
            EVENTS
                .map { it.name to nextOccurrence(it.monthDay, today) }
                .map { (name, date) -> Triple(name, date, ChronoUnit.DAYS.between(today, date)) }
                .filter { (_, _, days) -> days in 0..HORIZON_DAYS }
                .minByOrNull { (_, _, days) -> days }
                ?: return null
        val days = candidate.third
        val dLabel = if (days == 0L) "D-day" else "D-$days"
        return "${candidate.first} ($dLabel)"
    }

    /** [monthDay]의 [from] 이후(당일 포함) 가장 가까운 발생일. 올해가 지났으면 내년. */
    private fun nextOccurrence(
        monthDay: MonthDay,
        from: LocalDate,
    ): LocalDate {
        val thisYear = monthDay.atYear(from.year)
        return if (thisYear.isBefore(from)) monthDay.atYear(from.year + 1) else thisYear
    }

    private data class Event(
        val name: String,
        val monthDay: MonthDay,
    )

    private companion object {
        const val HORIZON_DAYS = 30L

        // 한국 꽃 수요 기념일(고정일). 졸업·입학 시즌은 대표일(시작 무렵)로 근사한다.
        val EVENTS =
            listOf(
                Event("발렌타인데이", MonthDay.of(2, 14)),
                Event("졸업·입학 시즌", MonthDay.of(2, 20)),
                Event("화이트데이", MonthDay.of(3, 14)),
                Event("어린이날", MonthDay.of(5, 5)),
                Event("어버이날", MonthDay.of(5, 8)),
                Event("로즈데이", MonthDay.of(5, 14)),
                Event("스승의날", MonthDay.of(5, 15)),
                Event("빼빼로데이", MonthDay.of(11, 11)),
                Event("크리스마스", MonthDay.of(12, 25)),
            )
    }
}
