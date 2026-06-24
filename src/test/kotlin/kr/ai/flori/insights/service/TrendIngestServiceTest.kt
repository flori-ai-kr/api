package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.insights.client.NaverSearchApiClient
import kr.ai.flori.insights.config.NaverSearchApiProperties
import kr.ai.flori.insights.domain.TrendQueries
import kr.ai.flori.insights.repository.TrendArticleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

/**
 * 네이버 뉴스 검색 적재 서비스 검증 — 라이브 API 대신 MockRestServiceServer 로 응답을 모킹한다.
 * 실제 Zonky PG 에 upsert 해 HTML 정제·멱등(중복 source_url)·no-op 을 검증한다. 공유 테이블이라 @AfterEach 에서 비운다.
 * 서비스가 TrendQueries.ALL 전 검색어를 순회하므로 테스트는 전 검색어 URL 을 stub 한다(첫 검색어만 데이터, 나머지 빈 응답).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class TrendIngestServiceTest {
    @Autowired
    lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    @Autowired
    lateinit var articleRepository: TrendArticleRepository

    @AfterEach
    fun tearDown() {
        articleRepository.deleteAll()
    }

    private val properties =
        NaverSearchApiProperties(
            baseUrl = "https://openapi.naver.com/v1/search",
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            display = 10,
        )

    /** 실제 응답 샘플(뉴스 1건) — title/description 에 <b> 하이라이트·HTML 엔티티가 섞여 온다. */
    private fun sampleJson(): String =
        """
        {"lastBuildDate":"Mon, 23 Jun 2026 14:12:00 +0900","total":1,"start":1,"display":1,"items":[
          {"title":"<b>꽃</b> 시장 동향 &amp; 전망",
           "originallink":"http://www.flowernews.co.kr/news/1",
           "link":"https://n.news.naver.com/article/1",
           "description":"올해 <b>화훼</b> 시장은 &quot;회복&quot;세",
           "pubDate":"Mon, 23 Jun 2026 14:12:00 +0900"}
        ]}
        """.trimIndent()

    private fun emptyJson(): String = """{"lastBuildDate":"","total":0,"start":1,"display":10,"items":[]}"""

    /** TrendQueries.ALL 전 검색어를 stub. keyword→응답 매핑(미지정 검색어는 빈 응답). */
    private fun ingestServiceWith(responsesByKeyword: Map<String, String>): TrendIngestService {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
        for (q in TrendQueries.ALL) {
            val uri =
                UriComponentsBuilder
                    .fromUriString(properties.baseUrl)
                    .path("/news.json")
                    .queryParam("query", q.keyword)
                    .queryParam("display", properties.display)
                    .queryParam("sort", "date")
                    .encode()
                    .build()
                    .toUri()
                    .toString()
            server
                .expect(ExpectedCount.once(), requestTo(uri))
                .andExpect(header("X-Naver-Client-Id", properties.clientId))
                .andExpect(header("X-Naver-Client-Secret", properties.clientSecret))
                .andRespond(withSuccess(responsesByKeyword[q.keyword] ?: emptyJson(), MediaType.APPLICATION_JSON))
        }
        val client = NaverSearchApiClient(builder, properties)
        return TrendIngestService(client, properties, jdbcTemplate)
    }

    @Test
    fun `뉴스를 파싱해 적재하고 HTML 태그·엔티티를 제거한다`() {
        val firstKeyword = TrendQueries.ALL.first().keyword
        val service = ingestServiceWith(mapOf(firstKeyword to sampleJson()))

        service.scheduledIngest()

        val rows = articleRepository.findAll()
        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.category).isEqualTo(TrendQueries.ALL.first().category)
        assertThat(row.title).isEqualTo("꽃 시장 동향 & 전망")
        assertThat(row.summary).isEqualTo("올해 화훼 시장은 \"회복\"세")
        assertThat(row.sourceUrl).isEqualTo("http://www.flowernews.co.kr/news/1")
        assertThat(row.sourceName).isEqualTo("flowernews.co.kr")
        assertThat(row.publishedAt).isEqualTo(Instant.parse("2026-06-23T05:12:00Z"))
        assertThat(row.keyPoints).isEmpty()
    }

    @Test
    fun `재실행 시 같은 기사(source_url)는 중복 적재되지 않는다`() {
        val firstKeyword = TrendQueries.ALL.first().keyword
        ingestServiceWith(mapOf(firstKeyword to sampleJson())).scheduledIngest()
        ingestServiceWith(mapOf(firstKeyword to sampleJson())).scheduledIngest()

        assertThat(articleRepository.count()).isEqualTo(1)
    }

    @Test
    fun `clientId·secret 미설정이면 적재하지 않는다(no-op)`() {
        val blank = properties.copy(clientId = "", clientSecret = "")
        val service =
            TrendIngestService(
                NaverSearchApiClient(RestClient.builder(), blank),
                blank,
                jdbcTemplate,
            )
        service.scheduledIngest()
        assertThat(articleRepository.count()).isZero()
    }
}
