package kr.ai.flori.insights.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kr.ai.flori.insights.config.NaverSearchApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

// ─── 네이버 검색 OpenAPI(뉴스) 계약 ────────────────────────────────────────
// GET /v1/search/news.json?query=&display=&sort=date — 인증은 헤더(X-Naver-Client-Id/Secret).
// title/description 에 <b> 하이라이트 태그와 HTML 엔티티가 섞여 온다 — 정제는 적재 서비스가 담당.
// pubDate 는 RFC 1123(예: "Mon, 23 Jun 2026 14:12:00 +0900"). 빈 결과: items=[].

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsResponse(
    val items: List<NaverNewsItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsItem(
    /** 제목(HTML 태그·엔티티 포함) → title. */
    val title: String? = null,
    /** 원문 언론사 URL → source_url(원천, 더 안정적). */
    val originallink: String? = null,
    /** 네이버 뉴스 URL → originallink 없을 때 source_url 폴백. */
    val link: String? = null,
    /** 본문 요약 스니펫(HTML 포함) → summary. */
    val description: String? = null,
    /** 발행 시각(RFC 1123) → published_at. */
    val pubDate: String? = null,
)

/**
 * 네이버 뉴스 검색 HTTP 클라이언트.
 *
 * RestClient.Builder 를 주입받아(테스트에서 MockRestServiceServer 바인딩 가능, 라이브 호출 금지).
 * 적재 cron(TrendIngestService)만 호출하며, clientId/clientSecret 은 properties(env)에서 온다.
 *
 * 한글 query 인코딩을 직접 통제하려고 미리 인코딩한 java.net.URI 를 넘긴다(문자열 템플릿 재인코딩 방지).
 */
@Component
class NaverSearchApiClient(
    builder: RestClient.Builder,
    private val properties: NaverSearchApiProperties,
) {
    private val restClient: RestClient = builder.build()

    /**
     * 뉴스 검색 단일 호출(최신순). 호출 실패는 호출자(적재 서비스)가 검색어 단위로 격리·로깅한다.
     */
    fun fetch(
        query: String,
        display: Int,
        sort: String,
    ): NaverNewsResponse {
        val uri =
            UriComponentsBuilder
                .fromUriString(properties.baseUrl)
                .path("/news.json")
                .queryParam("query", query)
                .queryParam("display", display)
                .queryParam("sort", sort)
                .encode()
                .build()
                .toUri()
        return restClient
            .get()
            .uri(uri)
            .header("X-Naver-Client-Id", properties.clientId)
            .header("X-Naver-Client-Secret", properties.clientSecret)
            .retrieve()
            .body(NaverNewsResponse::class.java) ?: NaverNewsResponse()
    }
}
