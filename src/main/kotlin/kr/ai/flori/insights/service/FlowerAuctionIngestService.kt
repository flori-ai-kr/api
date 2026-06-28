package kr.ai.flori.insights.service

import kr.ai.flori.common.job.JobNames
import kr.ai.flori.common.job.JobOutcome
import kr.ai.flori.common.job.JobRunRecorder
import kr.ai.flori.common.util.KST
import kr.ai.flori.insights.client.F001Item
import kr.ai.flori.insights.client.FlowerApiClient
import kr.ai.flori.insights.config.FlowerApiProperties
import kr.ai.flori.insights.domain.FlowerCategories
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Date
import java.time.LocalDate

/**
 * aT 화훼유통정보 f001 일별 경락가 적재(단일 시장 = aT 양재).
 *
 * 매일 KST(기본 06:30) 실행 시 최근 backfillDays(기본 3일)를 4개 flowerGubn(절화/관엽/난/춘란) 전부
 * 페이징 수집해 flower_auction_prices 에 upsert 한다(정산 지연/누락일 커버, 멱등).
 *
 * - upsert: ON CONFLICT (sale_date,flower_gubn,pum_name,good_name,lv_nm) DO UPDATE.
 *   DO NOTHING 이 아니라 DO UPDATE 인 이유: 정산이 지연되어 같은 행의 금액/수량이 사후 정정될 수 있어,
 *   재실행 시 최신 정산값으로 갱신해야 한다(멱등하면서 정정 반영).
 * - 빈 날(numOfRows=0/items 없음)은 조용히 건너뛴다.
 * - per-day/per-gubn 격리: 한 (날짜,gubn) 실패가 나머지를 막지 않는다(개별 try/catch + 로깅).
 * - serviceKey/baseUrl 미설정이면 no-op(경고 로그) — 키 없는 dev 부팅 가능.
 */
@Service
class FlowerAuctionIngestService(
    private val client: FlowerApiClient,
    private val properties: FlowerApiProperties,
    private val jdbcTemplate: JdbcTemplate,
    private val jobRunRecorder: JobRunRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${flori.flower-api.cron:0 30 6 * * *}", zone = "Asia/Seoul")
    fun scheduledIngest() {
        jobRunRecorder.record(JobNames.FLOWER_AUCTION_INGEST) { runIngest() }
    }

    /** 화훼 경매 적재 본문(스케줄/수동 공유). */
    fun runIngest(): JobOutcome {
        if (properties.baseUrl.isBlank() || properties.serviceKey.isBlank()) {
            log.warn("화훼 경매 적재 건너뜀: flori.flower-api base-url/service-key 미설정 (no-op)")
            return JobOutcome.skipped()
        }
        val today = LocalDate.now(KST)
        val days = (0 until properties.backfillDays.coerceAtLeast(1)).map { today.minusDays(it.toLong()) }
        var total = 0
        for (date in days) {
            for (code in FlowerCategories.CODES) {
                total += ingestDayGubnSafely(date, code)
            }
        }
        log.info("화훼 경매 적재 완료: days={} upserted={}", days, total)
        return JobOutcome.success(total, mapOf("days" to days.size))
    }

    /** (날짜, flowerGubn) 단건 적재 — 실패해도 예외를 삼키고 0을 반환(격리). */
    @Suppress("TooGenericExceptionCaught") // 외부 API/파싱/DB 다양한 예외 — 건별 격리 위해 일괄 포착
    private fun ingestDayGubnSafely(
        date: LocalDate,
        flowerGubn: String,
    ): Int =
        try {
            ingestDayGubn(date, flowerGubn)
        } catch (e: Exception) {
            log.error("화훼 경매 적재 실패: date={} flowerGubn={}", date, flowerGubn, e)
            0
        }

    /** (날짜, flowerGubn) 전 페이지 수집 후 upsert. 빈 날은 0건. */
    private fun ingestDayGubn(
        date: LocalDate,
        flowerGubn: String,
    ): Int {
        var page = 1
        var upserted = 0
        var hasMore = true
        while (hasMore) {
            val items =
                client
                    .fetch(date, flowerGubn, page)
                    .response
                    ?.items
                    .orEmpty()
            items.forEach { upserted += upsert(date, it) }
            // 가득 찬 페이지면 다음 페이지가 더 있을 수 있다. 비었거나 덜 찼으면 종료(빈 날 = 0건).
            hasMore = items.size >= properties.pageSize && items.isNotEmpty()
            page++
        }
        return upserted
    }

    /** 단일 행 upsert. 응답 saleDate 가 있으면 우선, 없으면 요청 baseDate 사용. */
    private fun upsert(
        baseDate: LocalDate,
        item: F001Item,
    ): Int {
        val saleDate = item.saleDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: baseDate
        val flowerGubn = item.flowerGubn?.takeIf { it.isNotBlank() } ?: return 0
        val pumName = item.pumName?.takeIf { it.isNotBlank() } ?: return 0
        return jdbcTemplate.update(
            UPSERT_SQL,
            Date.valueOf(saleDate),
            flowerGubn,
            pumName,
            item.goodName.orEmpty(),
            item.lvNm.orEmpty(),
            parseInt(item.avgAmt),
            parseInt(item.maxAmt),
            parseInt(item.minAmt),
            parseLong(item.totQty),
            parseLong(item.totAmt),
        )
    }

    /** 문자열 금액 → Int. 공백/널/파싱불가는 null. */
    private fun parseInt(value: String?): Int? = value?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** 문자열 수량/금액 → Long. 공백/널/파싱불가는 null. */
    private fun parseLong(value: String?): Long? = value?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    private companion object {
        val UPSERT_SQL =
            """
            INSERT INTO flower_auction_prices
                (sale_date, flower_gubn, pum_name, good_name, lv_nm, avg_amt, max_amt, min_amt, tot_qty, tot_amt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (sale_date, flower_gubn, pum_name, good_name, lv_nm) DO UPDATE SET
                avg_amt = EXCLUDED.avg_amt,
                max_amt = EXCLUDED.max_amt,
                min_amt = EXCLUDED.min_amt,
                tot_qty = EXCLUDED.tot_qty,
                tot_amt = EXCLUDED.tot_amt
            """.trimIndent()
    }
}
