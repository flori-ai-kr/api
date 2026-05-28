package kr.ai.flori.common.util

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 서비스 전역에서 사용하는 한국 표준시(KST). */
val KST: ZoneId = ZoneId.of("Asia/Seoul")

private const val YEAR_LENGTH = 4
private const val DAY_LENGTH = 10
private const val DECEMBER = 12
private const val LAST_DAY_DEC = 31

/**
 * month 문자열을 [start, end] 날짜 범위로 해석한다.
 * - "YYYY"      → 해당 연도 전체
 * - "YYYY-MM-DD"→ 단일 일자
 * - "YYYY-MM"   → 해당 월 전체
 * - null/blank  → null (필터 미적용)
 *
 * 형식이 잘못된 입력은 [CommonErrorCode.VALIDATION](400)으로 거부한다(파싱 예외가 500/Discord 알림으로
 * 새지 않도록 시스템 경계에서 차단).
 */
fun monthRange(month: String?): Pair<LocalDate, LocalDate>? {
    if (month.isNullOrBlank()) return null
    return try {
        when (month.length) {
            YEAR_LENGTH -> LocalDate.of(month.toInt(), 1, 1) to LocalDate.of(month.toInt(), DECEMBER, LAST_DAY_DEC)
            DAY_LENGTH -> LocalDate.parse(month).let { it to it }
            else -> YearMonth.parse(month).let { it.atDay(1) to it.atEndOfMonth() }
        }
    } catch (_: NumberFormatException) {
        throw AppException(CommonErrorCode.VALIDATION, "잘못된 기간(month) 형식입니다: $month")
    } catch (_: DateTimeException) {
        throw AppException(CommonErrorCode.VALIDATION, "잘못된 기간(month) 형식입니다: $month")
    }
}
