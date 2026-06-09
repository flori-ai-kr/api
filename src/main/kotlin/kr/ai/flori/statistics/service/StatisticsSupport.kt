package kr.ai.flori.statistics.service

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 통계 도메인 서비스가 공유하는 순수 계산 헬퍼·상수 모음.
 * 도메인별 SQL은 각 서비스가 소유하고, 여기에는 교차 도메인에서 재사용되는 비율·증감·기간 계산만 둔다.
 */
@Component
class StatisticsSupport {
    /**
     * 직전 동일 길이 기간을 계산한다. days = (to - from) + 1 일이며,
     * 직전 기간은 [from - days, from - 1] 이다.
     */
    fun previousPeriod(
        from: LocalDate,
        to: LocalDate,
    ): Period {
        val days = ChronoUnit.DAYS.between(from, to) + 1
        return Period(days, from.minusDays(days), from.minusDays(1))
    }

    /** 부분/전체 비율(%) 반올림. total<=0이면 0. */
    fun percentage(
        amount: Long,
        total: Long,
    ): Int = if (total > 0) (amount.toDouble() / total * PERCENT).roundToInt() else 0

    /** 직전 대비 증감률(%). prev=0이면 cur>0일 때 100, 아니면 0. Int 범위로 클램프. */
    fun pct(
        cur: Long,
        prev: Long,
    ): Int {
        if (prev == 0L) return if (cur > 0L) PERCENT else 0
        val v = (cur - prev) * PERCENT.toDouble() / prev
        return v.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).roundToInt()
    }

    data class Period(
        val days: Long,
        val from: LocalDate,
        val to: LocalDate,
    )

    companion object {
        const val PERCENT = 100

        /** id가 null인(설정에서 삭제됐거나 미지정) 집계 버킷의 표시 라벨. */
        const val ETC = "기타"
    }
}
