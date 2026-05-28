package kr.ai.flori.common.util

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

/**
 * monthRange 해석 규칙 + 잘못된 입력의 400(VALIDATION) 거부(특성화 테스트).
 */
class DateRangesTest {
    @Test
    fun `연도는 그 해 전체 범위`() {
        assertThat(monthRange("2026")).isEqualTo(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `월은 그 달 전체 범위`() {
        assertThat(monthRange("2026-05")).isEqualTo(LocalDate.of(2026, 5, 1) to LocalDate.of(2026, 5, 31))
    }

    @Test
    fun `일자는 단일 일`() {
        assertThat(monthRange("2026-05-22")).isEqualTo(LocalDate.of(2026, 5, 22) to LocalDate.of(2026, 5, 22))
    }

    @Test
    fun `null·blank는 필터 미적용(null)`() {
        assertThat(monthRange(null)).isNull()
        assertThat(monthRange("  ")).isNull()
    }

    @Test
    fun `숫자가 아닌 4자 입력은 VALIDATION`() {
        val ex = assertThrows<AppException> { monthRange("ABCD") }
        assertThat(ex.errorCode).isEqualTo(CommonErrorCode.VALIDATION)
    }

    @Test
    fun `형식이 잘못된 입력은 VALIDATION`() {
        assertThat(assertThrows<AppException> { monthRange("2026-13") }.errorCode).isEqualTo(CommonErrorCode.VALIDATION)
        assertThat(assertThrows<AppException> { monthRange("2026-99-99") }.errorCode).isEqualTo(CommonErrorCode.VALIDATION)
    }
}
