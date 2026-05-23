package com.hazel.sales.service

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 입금 계산 순수 함수(서버 SSOT). 외부 의존 없이 단위 테스트 가능.
 */
private const val PERCENT_BASE = 100
private val PERCENT = BigDecimal(PERCENT_BASE)

/** 카드 수수료 = round(amount * feeRate / 100), HALF_UP. */
internal fun computeFee(
    amount: Int,
    feeRate: BigDecimal,
): Int =
    BigDecimal(amount)
        .multiply(feeRate)
        .divide(PERCENT)
        .setScale(0, RoundingMode.HALF_UP)
        .toInt()

/** 입금예정일 = 매출일 + N영업일(주말 제외). */
internal fun addBusinessDays(
    start: LocalDate,
    businessDays: Int,
): LocalDate {
    var date = start
    var added = 0
    while (added < businessDays) {
        date = date.plusDays(1)
        if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
            added++
        }
    }
    return date
}
