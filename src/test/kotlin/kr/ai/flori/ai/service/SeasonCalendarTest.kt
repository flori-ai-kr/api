package kr.ai.flori.ai.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * SeasonCalendar 결정적 D-day 계산 단위테스트. 시스템 시계와 무관하게 입력 날짜로 검증한다.
 */
class SeasonCalendarTest {
    private val calendar = SeasonCalendar()

    @Test
    fun `30일 내 가장 가까운 기념일을 D-N으로 돌려준다`() {
        // 5/1 기준 향후 30일: 어린이날(5/5, D-4)이 가장 가깝다(어버이날 5/8보다 가까움)
        assertThat(calendar.upcoming(LocalDate.of(2026, 5, 1))).isEqualTo("어린이날 (D-4)")
    }

    @Test
    fun `당일은 D-day로 표기한다`() {
        assertThat(calendar.upcoming(LocalDate.of(2026, 5, 5))).isEqualTo("어린이날 (D-day)")
    }

    @Test
    fun `30일 내 기념일이 없으면 null`() {
        // 6/20 기준 다음 고정 기념일은 빼빼로데이(11/11) → 30일 초과 → null
        assertThat(calendar.upcoming(LocalDate.of(2026, 6, 20))).isNull()
    }

    @Test
    fun `연말 경계에서 다음 해 후보를 고른다`() {
        // 12/20 기준: 크리스마스(12/25, D-5)
        assertThat(calendar.upcoming(LocalDate.of(2026, 12, 20))).isEqualTo("크리스마스 (D-5)")
    }

    @Test
    fun `발렌타인데이 직전이면 발렌타인을 고른다`() {
        // 2/10 기준: 발렌타인(2/14, D-4)이 졸업입학 시즌(2/20)보다 가깝다
        assertThat(calendar.upcoming(LocalDate.of(2026, 2, 10))).isEqualTo("발렌타인데이 (D-4)")
    }
}
