package kr.ai.flori.statistics.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.statistics.dto.DowCount
import kr.ai.flori.statistics.dto.HeatCell
import kr.ai.flori.statistics.dto.HourCount
import kr.ai.flori.statistics.dto.ReservationKpi
import kr.ai.flori.statistics.dto.ReservationStatisticsResponse
import kr.ai.flori.statistics.dto.ReservationTimePoint
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 예약 통계. 픽업 매장 특성상 상태 필터 없이 전체 예약을 집계한다.
 * 시간대(hour)·히트맵은 time이 NULL인 행을 제외하지만, 총건수·시계열·요일 분포에는 포함한다.
 * KPI(최다 요일·피크 시간대)는 분포 결과에서 Kotlin으로 도출해 추가 쿼리를 피한다.
 */
@Service
class ReservationStatisticsService(
    private val jdbcTemplate: JdbcTemplate,
    private val support: StatisticsSupport,
) {
    @Transactional(readOnly = true)
    fun reservationStatistics(
        from: LocalDate,
        to: LocalDate,
    ): ReservationStatisticsResponse {
        if (from.isAfter(to)) throw AppException(CommonErrorCode.VALIDATION, "from must not be after to")
        if (ChronoUnit.DAYS.between(from, to) > StatisticsSupport.MAX_RANGE_DAYS) {
            throw AppException(CommonErrorCode.VALIDATION, "조회 기간이 너무 깁니다")
        }
        val userId = TenantContext.currentUserId()
        val prev = support.previousPeriod(from, to)
        // 현재 기간 길이(일). previousPeriod가 (to-from)+1로 산출한 동일 길이를 재사용한다.
        val periodDays = prev.days

        val total = reservationTotal(userId, from, to)
        val prevTotal = reservationTotal(userId, prev.from, prev.to)
        // 일평균을 소수 1자리로 반올림(예: 2.36 → 2.4).
        val dailyAvg = (total.toDouble() / periodDays * ONE_DECIMAL_SCALE).roundToInt() / ONE_DECIMAL_SCALE

        val dowDistribution = reservationDowDistribution(userId, from, to)
        val hourDistribution = reservationHourDistribution(userId, from, to)
        val heatmap = reservationHeatmap(userId, from, to)

        val busiest = dowDistribution.maxByOrNull { it.count }
        val peak = hourDistribution.maxByOrNull { it.count }

        val kpi =
            ReservationKpi(
                total = total,
                totalDeltaPct = support.pct(total, prevTotal),
                dailyAvg = dailyAvg,
                busiestDow = busiest?.dow ?: -1,
                busiestDowPct = busiest?.let { support.percentage(it.count, total) } ?: 0,
                peakHourBucket = peak?.hourBucket ?: "",
                peakHourPct = peak?.let { support.percentage(it.count, hourDistribution.sumOf { d -> d.count }) } ?: 0,
            )

        return ReservationStatisticsResponse(
            kpi = kpi,
            timeseries = reservationTimeseries(userId, from, to),
            heatmap = heatmap,
            dowDistribution = dowDistribution,
            hourDistribution = hourDistribution,
        )
    }

    /** 기간 내 전체 예약 건수(상태 필터 없음). */
    private fun reservationTotal(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reservations WHERE user_id = ?::bigint AND date BETWEEN ? AND ?",
            Long::class.java,
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
        ) ?: 0L

    /** 일별 예약 건수 시계열(예약 있는 날만, 일자 오름차순). */
    private fun reservationTimeseries(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<ReservationTimePoint> =
        jdbcTemplate.query(
            """
            SELECT date AS d, COUNT(*) AS cnt FROM reservations
            WHERE user_id = ?::bigint AND date BETWEEN ? AND ? GROUP BY date ORDER BY date
            """.trimIndent(),
            { rs, _ -> ReservationTimePoint(rs.getDate("d").toLocalDate(), rs.getLong("cnt")) },
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
        )

    /** 요일별 예약 분포(time NULL 포함). Postgres DOW: 0=일요일..6=토요일. */
    private fun reservationDowDistribution(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<DowCount> =
        jdbcTemplate.query(
            """
            SELECT EXTRACT(DOW FROM date)::int AS dow, COUNT(*) AS cnt FROM reservations
            WHERE user_id = ?::bigint AND date BETWEEN ? AND ? GROUP BY dow ORDER BY dow
            """.trimIndent(),
            { rs, _ -> DowCount(rs.getInt("dow"), rs.getLong("cnt")) },
            userId,
            Date.valueOf(from),
            Date.valueOf(to),
        )

    /** 시간대별 예약 분포(time NULL 제외). 표준 버킷 순서로 정렬. */
    private fun reservationHourDistribution(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<HourCount> =
        jdbcTemplate
            .query(
                """
                SELECT $HOUR_BUCKET_SQL AS bucket, COUNT(*) AS cnt FROM reservations
                WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND "time" IS NOT NULL
                GROUP BY bucket
                """.trimIndent(),
                // 어느 버킷에도 속하지 않는 시간(예: 09시 이전)은 bucket이 NULL → 집계에서 제외.
                { rs, _ -> rs.getString("bucket")?.let { HourCount(it, rs.getLong("cnt")) } },
                userId,
                Date.valueOf(from),
                Date.valueOf(to),
            ).filterNotNull()
            .sortedBy { HOUR_BUCKETS.indexOf(it.hourBucket) }

    /** 요일×시간대 히트맵(time NULL 제외). 요일·표준 버킷 순서로 정렬. */
    private fun reservationHeatmap(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<HeatCell> =
        jdbcTemplate
            .query(
                """
                SELECT EXTRACT(DOW FROM date)::int AS dow, $HOUR_BUCKET_SQL AS bucket, COUNT(*) AS cnt FROM reservations
                WHERE user_id = ?::bigint AND date BETWEEN ? AND ? AND "time" IS NOT NULL
                GROUP BY dow, bucket
                """.trimIndent(),
                // 어느 버킷에도 속하지 않는 시간(예: 09시 이전)은 bucket이 NULL → 집계에서 제외.
                { rs, _ -> rs.getString("bucket")?.let { HeatCell(rs.getInt("dow"), it, rs.getLong("cnt")) } },
                userId,
                Date.valueOf(from),
                Date.valueOf(to),
            ).filterNotNull()
            .sortedWith(compareBy({ it.dow }, { HOUR_BUCKETS.indexOf(it.hourBucket) }))

    private companion object {
        /** 일평균 소수 1자리 반올림 스케일((x*10).roundToInt()/10.0). */
        const val ONE_DECIMAL_SCALE = 10.0

        /** 예약 시간대 버킷 표준 순서. 정렬·결정성 보장에 사용. */
        val HOUR_BUCKETS = listOf("09-11", "11-13", "13-15", "15-17", "17-19", "19+")

        /**
         * time → 시간대 버킷 매핑 CASE(time NULL 행은 호출부 WHERE에서 제외).
         * time은 KST 리터럴 그대로 저장된다: JVM 기본 시간대가 시작 시 UTC로 고정(pinDefaultTimeZoneToUtc)되어
         * `hibernate.jdbc.time_zone=UTC`와 오프셋 차가 0이 되므로, LocalTime(15,30)은 DB에 15:30으로 그대로
         * 들어간다(시간대 환산 없음). 따라서 환산 없이 저장된 "time"에 직접 버킷팅한다.
         * 컬럼명 time은 예약어라 따옴표로 구분.
         */
        val HOUR_BUCKET_SQL =
            """
            CASE
              WHEN EXTRACT(HOUR FROM "time") >= 9 AND EXTRACT(HOUR FROM "time") < 11 THEN '09-11'
              WHEN EXTRACT(HOUR FROM "time") >= 11 AND EXTRACT(HOUR FROM "time") < 13 THEN '11-13'
              WHEN EXTRACT(HOUR FROM "time") >= 13 AND EXTRACT(HOUR FROM "time") < 15 THEN '13-15'
              WHEN EXTRACT(HOUR FROM "time") >= 15 AND EXTRACT(HOUR FROM "time") < 17 THEN '15-17'
              WHEN EXTRACT(HOUR FROM "time") >= 17 AND EXTRACT(HOUR FROM "time") < 19 THEN '17-19'
              WHEN EXTRACT(HOUR FROM "time") >= 19 THEN '19+'
            END
            """.trimIndent()
    }
}
