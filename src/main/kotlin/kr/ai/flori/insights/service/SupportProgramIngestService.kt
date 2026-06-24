package kr.ai.flori.insights.service

import kr.ai.flori.insights.client.KStartupAnnouncement
import kr.ai.flori.insights.client.KStartupApiClient
import kr.ai.flori.insights.config.KStartupApiProperties
import kr.ai.flori.insights.domain.GrantRelevance
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 창업진흥원 K-Startup 사업공고 적재.
 *
 * 매일 KST(기본 07:00) 실행 시 최신순(pbanc_sn desc)으로 pages 페이지를 수집해 support_programs 에 upsert 한다.
 *
 * - upsert: ON CONFLICT (source, source_id) DO UPDATE. 같은 공고가 사후 수정될 수 있어 최신값으로 갱신(멱등 + 정정).
 * - 빈 페이지(data=[])면 더 없는 것으로 보고 중단.
 * - 페이지 단위 격리: 한 페이지 실패가 나머지를 막지 않는다(개별 try/catch + 로깅).
 * - serviceKey/baseUrl 미설정이면 no-op(경고 로그) — 키 없는 dev 부팅 가능.
 */
@Service
class SupportProgramIngestService(
    private val client: KStartupApiClient,
    private val properties: KStartupApiProperties,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${flori.kstartup-api.cron:0 0 7 * * *}", zone = "Asia/Seoul")
    fun scheduledIngest() {
        if (properties.baseUrl.isBlank() || properties.serviceKey.isBlank()) {
            log.warn("지원사업 적재 건너뜀: flori.kstartup-api base-url/service-key 미설정 (no-op)")
            return
        }
        var total = 0
        var pages = 0
        for (page in 1..properties.pages.coerceAtLeast(1)) {
            val announcements = fetchPageSafely(page)
            if (announcements.isEmpty()) break
            announcements.forEach { total += upsert(it) }
            pages++
        }
        log.info("지원사업 적재 완료: pages={} upserted={}", pages, total)
    }

    /** 단일 페이지 조회 — 실패해도 예외를 삼키고 빈 목록 반환(격리, 루프 중단). */
    @Suppress("TooGenericExceptionCaught") // 외부 API/파싱 다양한 예외 — 페이지 격리 위해 일괄 포착
    private fun fetchPageSafely(page: Int): List<KStartupAnnouncement> =
        try {
            client.fetch(page, properties.pageSize).data
        } catch (e: Exception) {
            log.error("지원사업 적재 실패: page={}", page, e)
            emptyList()
        }

    /** 단일 공고 upsert. pbanc_sn/공고명 없거나 소상공인(꽃집) 무관이면 skip. */
    private fun upsert(item: KStartupAnnouncement): Int {
        val sourceId = item.pbancSn?.toString()
        val title =
            item.bizPbancNm?.takeIf { it.isNotBlank() }
                ?: item.intgPbancBizNm?.takeIf { it.isNotBlank() }
        if (sourceId == null || title == null || !GrantRelevance.isRelevant(title, item.pbancCtnt, item.aplyTrgtCtnt)) {
            return 0
        }
        return jdbcTemplate.update(
            UPSERT_SQL,
            SOURCE,
            sourceId,
            title,
            item.pbancNtrpNm,
            mapCategory(item.suptBizClsfc),
            item.aplyTrgtCtnt,
            item.pbancCtnt,
            parseYmd(item.pbancRcptBgngDt)?.let(Date::valueOf),
            parseYmd(item.pbancRcptEndDt)?.let(Date::valueOf),
            item.detlPgUrl,
        )
    }

    /** K-Startup 지원분야(supt_biz_clsfc) → category(fund/marketing/education). 미매칭은 null. */
    private fun mapCategory(clsfc: String?): String? {
        val c = clsfc ?: return null
        return when {
            c.contains("자금") || c.contains("사업화") || c.contains("기술개발") || c.contains("R&D") -> "fund"
            c.contains("마케팅") || c.contains("판로") || c.contains("해외진출") -> "marketing"
            c.contains("교육") || c.contains("멘토") || c.contains("컨설팅") -> "education"
            else -> null
        }
    }

    /** yyyyMMdd 문자열 → LocalDate. 형식 불일치/공백은 null. */
    private fun parseYmd(value: String?): LocalDate? =
        value?.trim()?.takeIf { it.length == YMD_LENGTH }?.let { runCatching { LocalDate.parse(it, YMD) }.getOrNull() }

    private companion object {
        const val SOURCE = "k-startup"
        const val YMD_LENGTH = 8
        val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val UPSERT_SQL =
            """
            INSERT INTO support_programs
                (source, source_id, title, agency, category, target, summary, apply_start, apply_end, source_url, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS DATE), CAST(? AS DATE), ?, NOW())
            ON CONFLICT (source, source_id) DO UPDATE SET
                title = EXCLUDED.title,
                agency = EXCLUDED.agency,
                category = EXCLUDED.category,
                target = EXCLUDED.target,
                summary = EXCLUDED.summary,
                apply_start = EXCLUDED.apply_start,
                apply_end = EXCLUDED.apply_end,
                source_url = EXCLUDED.source_url,
                collected_at = NOW()
            """.trimIndent()
    }
}
