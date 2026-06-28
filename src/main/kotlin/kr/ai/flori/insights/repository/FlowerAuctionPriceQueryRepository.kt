package kr.ai.flori.insights.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 경매 시세 파생 조회 전용 리포지토리(네이티브 SQL). 공유 읽기 — user_id 격리 대상이 아니다.
 *
 * 단일 시장(aT 양재) — 시장/법인 구분이 없다(flower_markets 폐기).
 * 등락률은 컬럼이 아니라 파생 계산이다: 같은 (flower_gubn, pum_name, good_name, lv_nm)에 대해
 * 정산일자(sale_date) 순으로 직전 정산일자의 평균단가(prev_avg_amt)를 LAG 윈도로 구해
 * 대상일 행만 남기고 (avg - prev)/prev 로 changeRate 를 계산한다(idx_fap_item_date 활용).
 */
@Repository
class FlowerAuctionPriceQueryRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    /** 데이터가 있는 가장 최근 정산일자(필터 적용 후). 없으면 null. */
    fun latestDate(
        gubn: String?,
        item: String?,
    ): LocalDate? =
        jdbcTemplate
            .query(
                """
                SELECT MAX(sale_date) AS d
                FROM flower_auction_prices
                WHERE (?::text IS NULL OR flower_gubn = ?::text)
                  AND (?::text IS NULL OR pum_name = ?::text)
                """.trimIndent(),
                { rs, _ -> rs.getDate("d")?.toLocalDate() },
                gubn,
                gubn,
                item,
                item,
            ).firstOrNull()

    /**
     * 데이터가 있는 정산일자(필터 적용 후) 최신순 distinct 목록. 날짜 선택기(date picker)용.
     * cap 으로 상한(기본 약 60일)을 둔다.
     */
    fun distinctDates(
        gubn: String?,
        cap: Int,
    ): List<LocalDate> =
        jdbcTemplate.query(
            """
            SELECT DISTINCT sale_date AS d
            FROM flower_auction_prices
            WHERE (?::text IS NULL OR flower_gubn = ?::text)
            ORDER BY d DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getDate("d").toLocalDate() },
            gubn,
            gubn,
            cap,
        )

    /**
     * 기본 기준일 = "완전한(complete) 최신 정산일". 최근 14일 중 일별 행 수의 최댓값을 구해,
     * 그 절반(0.5×) 이상인 정산일 가운데 가장 최근 날짜를 반환한다(부분/미완 적재일 스킵).
     * 자격 일자가 없으면(데이터가 있어도) 가장 최근 정산일로 폴백. 데이터가 전혀 없으면 null.
     * gubn 필터가 주어지면 그 구분 안에서만 행 수를 센다.
     */
    fun latestCompleteDate(gubn: String?): LocalDate? =
        jdbcTemplate
            .query(
                COMPLETE_DATE_SQL,
                { rs, _ -> rs.getDate("d")?.toLocalDate() },
                gubn,
                gubn,
            ).firstOrNull()

    /**
     * 대상일의 시세 행 + 직전 정산일자 대비 등락률(파생).
     * changeRate 는 직전 평균가가 없거나 0이면 null.
     */
    fun ratesOn(
        date: LocalDate,
        gubn: String?,
        item: String?,
    ): List<AuctionPriceRow> =
        jdbcTemplate.query(
            RATES_SQL,
            { rs, _ -> mapRow(rs) },
            date,
            gubn,
            gubn,
            item,
            item,
            date,
        )

    /**
     * 대상일의 품목(pum_name) 단위 요약 목록(거래량 많은 순).
     * - repAvg = 거래량 가중평균 = round(sum(tot_amt)/sum(tot_qty)). 거래량 0/없으면 null.
     * - repChangeRate = 등락 방식 A: 같은 품목의 각 (good_name, lv_nm) 행의 직전 정산일 대비 등락률 중
     *   양일 모두 존재(non-null)하는 품종·등급만 추려 **중앙값**(percentile_cont(0.5))을 SQL에서 계산. 없으면 null.
     * - variantCount = 그 품목·그날의 (good_name, lv_nm) 행 수.
     */
    fun summaryOn(
        date: LocalDate,
        gubn: String?,
    ): List<AuctionSummaryRow> =
        jdbcTemplate.query(
            SUMMARY_SQL,
            { rs, _ -> mapSummaryRow(rs) },
            date,
            gubn,
            gubn,
            date,
        )

    private fun mapSummaryRow(rs: java.sql.ResultSet): AuctionSummaryRow =
        AuctionSummaryRow(
            pumName = rs.getString("pum_name"),
            repAvg = rs.getObject("rep_avg") as? Int,
            repChangeRate = rs.getObject("rep_change_rate") as? Double,
            variantCount = rs.getLong("variant_count"),
        )

    private fun mapRow(rs: java.sql.ResultSet): AuctionPriceRow {
        val avg = rs.getObject("avg_amt") as? Int
        val prev = rs.getObject("prev_avg_amt") as? Int
        val changeRate =
            if (avg != null && prev != null && prev != 0) (avg - prev).toDouble() / prev else null
        return AuctionPriceRow(
            flowerGubn = rs.getString("flower_gubn"),
            pumName = rs.getString("pum_name"),
            goodName = rs.getString("good_name"),
            lvNm = rs.getString("lv_nm"),
            avgAmt = avg,
            maxAmt = rs.getObject("max_amt") as? Int,
            minAmt = rs.getObject("min_amt") as? Int,
            totQty = rs.getObject("tot_qty") as? Long,
            totAmt = rs.getObject("tot_amt") as? Long,
            prevAvgAmt = prev,
            changeRate = changeRate,
        )
    }

    private companion object {
        // 최근 14일 일별 행 수의 최댓값 * 0.5 를 임계로, 그 이상인 정산일 중 최신을 고른다(부분 적재일 스킵).
        // 자격 일자가 없으면 전체에서 가장 최근 정산일로 폴백(COALESCE 의 두 번째 항).
        val COMPLETE_DATE_SQL =
            """
            WITH daily AS (
                SELECT sale_date, COUNT(*) AS cnt
                FROM flower_auction_prices
                WHERE (?::text IS NULL OR flower_gubn = ?::text)
                GROUP BY sale_date
            ),
            windowed AS (
                SELECT sale_date, cnt
                FROM daily
                WHERE sale_date >= (SELECT MAX(sale_date) FROM daily) - INTERVAL '14 days'
            ),
            threshold AS (
                SELECT MAX(cnt) * 0.5 AS min_cnt FROM windowed
            )
            SELECT COALESCE(
                (SELECT MAX(d.sale_date)
                 FROM daily d, threshold t
                 WHERE d.cnt >= t.min_cnt),
                (SELECT MAX(sale_date) FROM daily)
            ) AS d
            """.trimIndent()

        // 품목 단위 요약: 가중평균 + 등락 방식 A(매칭 품종·등급 중앙값) + 품종·등급 행 수.
        // lagged 로 직전 정산일 평균가를 구해 대상일 행만 남기고, 양일 모두 존재하는 변형의 등락률만 중앙값.
        val SUMMARY_SQL =
            """
            WITH lagged AS (
                SELECT p.sale_date,
                       p.flower_gubn,
                       p.pum_name,
                       p.good_name,
                       p.lv_nm,
                       p.avg_amt,
                       p.tot_qty,
                       p.tot_amt,
                       LAG(p.avg_amt) OVER (
                           PARTITION BY p.flower_gubn, p.pum_name, p.good_name, p.lv_nm
                           ORDER BY p.sale_date
                       ) AS prev_avg_amt
                FROM flower_auction_prices p
                WHERE p.sale_date <= ?::date
                  AND (?::text IS NULL OR p.flower_gubn = ?::text)
            ),
            today AS (
                SELECT pum_name,
                       avg_amt,
                       tot_qty,
                       tot_amt,
                       CASE
                           WHEN prev_avg_amt IS NOT NULL AND prev_avg_amt <> 0
                               THEN (avg_amt - prev_avg_amt)::double precision / prev_avg_amt
                           ELSE NULL
                       END AS change_rate
                FROM lagged
                WHERE sale_date = ?::date
            )
            SELECT pum_name,
                   CASE
                       WHEN SUM(tot_qty) > 0
                           THEN ROUND(SUM(tot_amt)::numeric / SUM(tot_qty))::int
                       ELSE NULL
                   END AS rep_avg,
                   percentile_cont(0.5) WITHIN GROUP (
                       ORDER BY change_rate
                   ) FILTER (WHERE change_rate IS NOT NULL) AS rep_change_rate,
                   COUNT(*) AS variant_count
            FROM today
            GROUP BY pum_name
            ORDER BY COALESCE(SUM(tot_qty), 0) DESC, pum_name
            """.trimIndent()

        // 직전 정산일자 평균가를 LAG 윈도로 구한 뒤 대상일 행만 남기고 등락률을 계산한다(idx_fap_item_date).
        val RATES_SQL =
            """
            WITH lagged AS (
                SELECT p.sale_date,
                       p.flower_gubn,
                       p.pum_name,
                       p.good_name,
                       p.lv_nm,
                       p.avg_amt,
                       p.max_amt,
                       p.min_amt,
                       p.tot_qty,
                       p.tot_amt,
                       LAG(p.avg_amt) OVER (
                           PARTITION BY p.flower_gubn, p.pum_name, p.good_name, p.lv_nm
                           ORDER BY p.sale_date
                       ) AS prev_avg_amt
                FROM flower_auction_prices p
                WHERE p.sale_date <= ?::date
                  AND (?::text IS NULL OR p.flower_gubn = ?::text)
                  AND (?::text IS NULL OR p.pum_name = ?::text)
            )
            SELECT l.flower_gubn,
                   l.pum_name,
                   l.good_name,
                   l.lv_nm,
                   l.avg_amt,
                   l.max_amt,
                   l.min_amt,
                   l.tot_qty,
                   l.tot_amt,
                   l.prev_avg_amt
            FROM lagged l
            WHERE l.sale_date = ?::date
            ORDER BY l.flower_gubn, l.pum_name, l.good_name, l.lv_nm
            """.trimIndent()
    }
}

/** 품목(pum_name) 단위 요약 한 행(파생 가중평균·중앙값 등락률 포함). 서비스가 DTO로 변환. */
data class AuctionSummaryRow(
    val pumName: String,
    val repAvg: Int?,
    val repChangeRate: Double?,
    val variantCount: Long,
)

/** 경매 시세 한 행(파생 등락률 포함). 서비스가 DTO로 변환. */
data class AuctionPriceRow(
    val flowerGubn: String,
    val pumName: String,
    val goodName: String,
    val lvNm: String,
    val avgAmt: Int?,
    val maxAmt: Int?,
    val minAmt: Int?,
    val totQty: Long?,
    val totAmt: Long?,
    val prevAvgAmt: Int?,
    val changeRate: Double?,
)
