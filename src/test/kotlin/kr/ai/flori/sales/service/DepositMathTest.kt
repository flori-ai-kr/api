package kr.ai.flori.sales.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DepositMathTest {
    @Test
    fun `카드 수수료는 HALF_UP 반올림으로 계산된다`() {
        assertThat(computeFee(100_000, BigDecimal("2.0"))).isEqualTo(2_000)
        assertThat(computeFee(10_000, BigDecimal("2.5"))).isEqualTo(250)
        // 33333 * 2% = 666.66 → 667
        assertThat(computeFee(33_333, BigDecimal("2.0"))).isEqualTo(667)
    }

    @Test
    fun `영업일은 주말을 건너뛴다`() {
        val friday = LocalDate.of(2026, 5, 22)
        assertThat(addBusinessDays(friday, 1)).isEqualTo(LocalDate.of(2026, 5, 25)) // 월
        assertThat(addBusinessDays(friday, 3)).isEqualTo(LocalDate.of(2026, 5, 27)) // 수
    }

    @Test
    fun `0 영업일은 같은 날짜를 반환한다`() {
        val day = LocalDate.of(2026, 5, 20)
        assertThat(addBusinessDays(day, 0)).isEqualTo(day)
    }
}
