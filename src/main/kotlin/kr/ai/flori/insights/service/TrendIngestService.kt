package kr.ai.flori.insights.service

import kr.ai.flori.common.util.KST
import kr.ai.flori.insights.client.NaverNewsItem
import kr.ai.flori.insights.client.NaverSearchApiClient
import kr.ai.flori.insights.config.NaverSearchApiProperties
import kr.ai.flori.insights.domain.TrendQueries
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.URI
import java.sql.Date
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 네이버 뉴스 검색으로 트렌드·뉴스 기사를 적재.
 *
 * 매일 KST(기본 07:30) 실행 시 TrendQueries.ALL 의 카테고리별 검색어로 최신 뉴스를 수집해 trend_articles 에 적재한다.
 *
 * - upsert: ON CONFLICT (source_url) DO NOTHING. 기사 본문은 발행 후 바뀌지 않는(append-only) 큐레이션이라
 *   먼저 본 값을 유지한다(멱등, 같은 기사가 여러 검색어에 걸려도 1건).
 * - title/description 의 <b> 태그·HTML 엔티티를 제거해 적재한다. summary 가 비면 제목으로 대체(NOT NULL).
 * - 검색어 단위 격리: 한 검색어 실패가 나머지를 막지 않는다(개별 try/catch + 로깅).
 * - clientId/clientSecret 미설정이면 no-op(경고 로그) — 키 없는 dev 부팅 가능.
 * - key_points 는 v1 에서 빈 배열(스키마 DEFAULT '[]'). AI 요약은 후속.
 */
@Service
class TrendIngestService(
    private val client: NaverSearchApiClient,
    private val properties: NaverSearchApiProperties,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${flori.naver-search-api.cron:0 30 7 * * *}", zone = "Asia/Seoul")
    fun scheduledIngest() {
        if (properties.baseUrl.isBlank() || properties.clientId.isBlank() || properties.clientSecret.isBlank()) {
            log.warn("트렌드 적재 건너뜀: flori.naver-search-api base-url/client-id/client-secret 미설정 (no-op)")
            return
        }
        val today = LocalDate.now(KST)
        var total = 0
        for (query in TrendQueries.ALL) {
            total += ingestQuerySafely(query, today)
        }
        log.info("트렌드 적재 완료: queries={} upserted={}", TrendQueries.ALL.size, total)
    }

    /** 단일 검색어 적재 — 실패해도 예외를 삼키고 0 을 반환(격리). */
    @Suppress("TooGenericExceptionCaught") // 외부 API/파싱 다양한 예외 — 검색어 격리 위해 일괄 포착
    private fun ingestQuerySafely(
        query: TrendQueries.Query,
        collectedAt: LocalDate,
    ): Int =
        try {
            client
                .fetch(query.keyword, properties.display, "date")
                .items
                .sumOf { upsert(query.category, it, collectedAt) }
        } catch (e: Exception) {
            log.error("트렌드 적재 실패: keyword={}", query.keyword, e)
            0
        }

    /** 단일 기사 upsert. source_url/제목 없으면 skip. summary 가 비면 제목으로 대체. */
    private fun upsert(
        category: String,
        item: NaverNewsItem,
        collectedAt: LocalDate,
    ): Int {
        val sourceUrl =
            (item.originallink?.takeIf { it.isNotBlank() } ?: item.link)?.trim()?.takeIf { it.isNotBlank() } ?: return 0
        val title = cleanHtml(item.title).takeIf { it.isNotBlank() } ?: return 0
        val summary = cleanHtml(item.description).ifBlank { title }
        return jdbcTemplate.update(
            UPSERT_SQL,
            category,
            title,
            summary,
            sourceUrl,
            hostOf(sourceUrl),
            parsePubDate(item.pubDate)?.toString(),
            Date.valueOf(collectedAt),
        )
    }

    /** <b> 등 태그 제거 + 흔한 HTML 엔티티 복원. &amp; 는 마지막에 풀어 이중 복원 방지. */
    private fun cleanHtml(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace(TAG_REGEX, "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .trim()
    }

    /** source_url 의 호스트(www. 제거) → source_name. 파싱 실패는 null. */
    private fun hostOf(url: String): String? = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * RFC 1123 pubDate(예: "Mon, 23 Jun 2026 14:12:00 +0900") → Instant. 형식 불일치/공백은 null.
     * 요일(EEE)은 날짜와의 정합 검증 충돌(피드가 틀린 요일을 줄 수 있음)을 피하려고 떼고 파싱한다.
     */
    private fun parsePubDate(raw: String?): Instant? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withoutDayOfWeek = s.substringAfter(", ", s)
        return runCatching { OffsetDateTime.parse(withoutDayOfWeek, PUBDATE_FORMAT).toInstant() }.getOrNull()
    }

    private companion object {
        val TAG_REGEX = Regex("<[^>]+>")
        val PUBDATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
        val UPSERT_SQL =
            """
            INSERT INTO trend_articles
                (category, title, summary, source_url, source_name, published_at, collected_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), ?)
            ON CONFLICT (source_url) DO NOTHING
            """.trimIndent()
    }
}
