package kr.ai.flori.insights.service

import kr.ai.flori.insights.client.BizinfoApiClient
import kr.ai.flori.insights.client.BizinfoItem
import kr.ai.flori.insights.config.BizinfoApiProperties
import kr.ai.flori.insights.domain.GrantRelevance
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.URI
import java.sql.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 기업마당(bizinfo) 지원사업 공고 적재 — 소진공·지자체·중기부 통합 집계.
 *
 * 매일 KST(기본 08:00) 실행 시 최신순으로 pages 페이지를 수집해 support_programs 에 source='bizinfo' 로 upsert 한다.
 * K-Startup(SupportProgramIngestService)과 같은 테이블·UPSERT 문을 쓰며 source 값과 필드 매핑만 다르다.
 *
 * - upsert: ON CONFLICT (source, source_id) DO UPDATE. 같은 공고가 사후 수정될 수 있어 최신값으로 갱신(멱등 + 정정).
 * - 빈 페이지(jsonArray=[])면 더 없는 것으로 보고 중단.
 * - 페이지 단위 격리: 한 페이지 실패가 나머지를 막지 않는다(개별 try/catch + 로깅).
 * - baseUrl/crtfcKey 미설정이면 no-op(경고 로그) — 키 없는 dev 부팅 가능.
 */
@Service
class BizinfoIngestService(
    private val client: BizinfoApiClient,
    private val properties: BizinfoApiProperties,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** baseUrl 의 scheme+host(상대 pblancUrl 절대경로화에 사용). baseUrl 비면 빈 문자열. */
    private val host: String =
        properties.baseUrl
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { URI(it).let { u -> "${u.scheme}://${u.authority}" } }.getOrNull() }
            .orEmpty()

    @Scheduled(cron = "\${flori.bizinfo-api.cron:0 0 8 * * *}", zone = "Asia/Seoul")
    fun scheduledIngest() {
        if (properties.baseUrl.isBlank() || properties.crtfcKey.isBlank()) {
            log.warn("기업마당 적재 건너뜀: flori.bizinfo-api base-url/crtfc-key 미설정 (no-op)")
            return
        }
        var total = 0
        var pages = 0
        for (page in 1..properties.pages.coerceAtLeast(1)) {
            val items = fetchPageSafely(page)
            if (items.isEmpty()) break
            items.forEach { total += upsert(it) }
            pages++
        }
        log.info("기업마당 적재 완료: pages={} upserted={}", pages, total)
    }

    /** 단일 페이지 조회 — 실패해도 예외를 삼키고 빈 목록 반환(격리, 루프 중단). */
    @Suppress("TooGenericExceptionCaught") // 외부 API/파싱 다양한 예외 — 페이지 격리 위해 일괄 포착
    private fun fetchPageSafely(page: Int): List<BizinfoItem> =
        try {
            client.fetch(page, properties.pageUnit).jsonArray
        } catch (e: Exception) {
            log.error("기업마당 적재 실패: page={}", page, e)
            emptyList()
        }

    /** 단일 공고 upsert. pblancId/공고명 없거나 소상공인(꽃집) 무관이면 skip. */
    private fun upsert(item: BizinfoItem): Int {
        val sourceId = item.pblancId?.takeIf { it.isNotBlank() }
        val title = item.pblancNm?.takeIf { it.isNotBlank() }
        if (sourceId == null || title == null || !GrantRelevance.isRelevant(title, item.bsnsSumryCn, item.trgetNm)) {
            return 0
        }
        val (start, end) = parsePeriod(item.reqstBeginEndDe)
        return jdbcTemplate.update(
            UPSERT_SQL,
            SOURCE,
            sourceId,
            title,
            item.jrsdInsttNm,
            mapCategory(item.pldirSportRealmLclasCodeNm),
            item.trgetNm,
            item.bsnsSumryCn,
            start?.let(Date::valueOf),
            end?.let(Date::valueOf),
            absolutize(item.pblancUrl),
        )
    }

    /** bizinfo 지원분야 대분류명 → category(fund/marketing/education). 미매칭은 null. */
    private fun mapCategory(realm: String?): String? {
        val c = realm ?: return null
        return CATEGORY_KEYWORDS.firstOrNull { (keywords, _) -> keywords.any(c::contains) }?.second
    }

    /** "YYYYMMDD ~ YYYYMMDD" → (시작, 마감). 상시/공백/한쪽없음은 해당 값 null. */
    private fun parsePeriod(value: String?): Pair<LocalDate?, LocalDate?> {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null to null
        val parts = raw.split("~")
        return parseYmd(parts.getOrNull(0)) to parseYmd(parts.getOrNull(1))
    }

    /** 숫자만 추려 yyyyMMdd(8자리)면 LocalDate, 아니면 null(상시·예산소진시 등). */
    private fun parseYmd(value: String?): LocalDate? {
        val digits = value?.filter(Char::isDigit) ?: return null
        return digits.takeIf { it.length == YMD_LENGTH }?.let { runCatching { LocalDate.parse(it, YMD) }.getOrNull() }
    }

    /** 상대경로(/...) pblancUrl 에 호스트를 붙여 절대 URL 로. 절대/빈값은 그대로. */
    private fun absolutize(url: String?): String? {
        val u = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (u.startsWith("/")) host + u else u
    }

    private companion object {
        const val SOURCE = "bizinfo"
        const val YMD_LENGTH = 8
        val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        /** 지원분야 대분류명 부분일치 → category. 위에서부터 먼저 매칭되는 것을 쓴다. */
        val CATEGORY_KEYWORDS =
            listOf(
                listOf("금융", "자금", "기술", "R&D") to "fund",
                listOf("수출", "내수", "판로", "마케팅", "해외") to "marketing",
                listOf("창업", "경영", "인력", "교육", "컨설팅", "멘토") to "education",
            )
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
