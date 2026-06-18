package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.insights.client.FlowerApiClient
import kr.ai.flori.insights.config.FlowerApiProperties
import kr.ai.flori.insights.repository.FlowerAuctionPriceQueryRepository
import kr.ai.flori.insights.repository.FlowerAuctionPriceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate

/**
 * f001 적재 서비스 검증 — 라이브 API 를 호출하지 않고 MockRestServiceServer 로 f001 응답을 모킹한다.
 * 실제 Zonky PG 에 upsert 해 멱등/정정 동작을 검증한다. 공유 테이블이라 @AfterEach 에서 비운다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class FlowerAuctionIngestServiceTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var priceRepository: FlowerAuctionPriceRepository

    @Autowired
    lateinit var queryRepository: FlowerAuctionPriceQueryRepository

    @AfterEach
    fun tearDown() {
        priceRepository.deleteAll()
    }

    private val properties =
        FlowerApiProperties(
            baseUrl = "https://flower.at.or.kr/api/returnData.api",
            serviceKey = "deadbeefdeadbeefdeadbeefdeadbeef",
            backfillDays = 1,
            pageSize = 1000,
        )

    /** 실제 응답 샘플(거베라) — 금액은 STRING 으로 온다. */
    private fun sampleJson(
        date: String,
        avg: String,
    ): String =
        """
        {"response":{"resultCd":"0","resultMsg":"OK","numOfRows":"1","items":[
          {"saleDate":"$date","flowerGubn":"절화","pumName":"거베라","goodName":"미니(혼합)","lvNm":"특2",
           "maxAmt":"4300","minAmt":"1050","avgAmt":"$avg","totAmt":"12816580","totQty":"3952"}
        ]}}
        """.trimIndent()

    private fun emptyJson(): String = """{"response":{"resultCd":"0","resultMsg":"OK","numOfRows":"0","items":[]}}"""

    /** baseDate 하루 + 4개 flowerGubn 호출을 stub 하는 클라이언트/모킹 세트. */
    private fun ingestServiceWith(
        date: LocalDate,
        responses: Map<String, String>,
    ): FlowerAuctionIngestService {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
        for (gubn in listOf("1", "2", "3", "4")) {
            val uri =
                UriComponentsBuilder
                    .fromUriString(properties.baseUrl)
                    .queryParam("kind", "f001")
                    .queryParam("serviceKey", properties.serviceKey)
                    .queryParam("baseDate", date.toString())
                    .queryParam("flowerGubn", gubn)
                    .queryParam("dataType", "json")
                    .queryParam("countPerPage", properties.pageSize)
                    .queryParam("currentPage", 1)
                    .build()
                    .toUriString()
            server
                .expect(ExpectedCount.once(), requestTo(uri))
                .andRespond(withSuccess(responses.getValue(gubn), MediaType.APPLICATION_JSON))
        }
        val client = FlowerApiClient(builder, properties)
        return FlowerAuctionIngestService(client, properties, jdbcTemplate)
    }

    @Test
    fun `f001 응답을 파싱해 적재하고 STRING 금액을 숫자로 변환한다`() {
        val date = LocalDate.now()
        // 절화(1)만 데이터, 나머지 gubn 은 빈 날.
        val service =
            ingestServiceWith(
                date,
                mapOf(
                    "1" to sampleJson(date.toString(), "3243"),
                    "2" to emptyJson(),
                    "3" to emptyJson(),
                    "4" to emptyJson(),
                ),
            )

        service.scheduledIngest()

        val rows = queryRepository.ratesOn(date, null, null)
        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.flowerGubn).isEqualTo("절화")
        assertThat(row.pumName).isEqualTo("거베라")
        assertThat(row.goodName).isEqualTo("미니(혼합)")
        assertThat(row.lvNm).isEqualTo("특2")
        assertThat(row.avgAmt).isEqualTo(3243)
        assertThat(row.maxAmt).isEqualTo(4300)
        assertThat(row.minAmt).isEqualTo(1050)
        assertThat(row.totQty).isEqualTo(3952L)
        assertThat(row.totAmt).isEqualTo(12_816_580L)
    }

    @Test
    fun `재실행 시 같은 키는 멱등 upsert 되고 금액 정정이 반영된다`() {
        val date = LocalDate.now()
        ingestServiceWith(
            date,
            mapOf("1" to sampleJson(date.toString(), "3000"), "2" to emptyJson(), "3" to emptyJson(), "4" to emptyJson()),
        ).scheduledIngest()
        // 정산 정정: 같은 키, avg 만 다른 값으로 재적재.
        ingestServiceWith(
            date,
            mapOf("1" to sampleJson(date.toString(), "3500"), "2" to emptyJson(), "3" to emptyJson(), "4" to emptyJson()),
        ).scheduledIngest()

        assertThat(priceRepository.count()).isEqualTo(1)
        assertThat(queryRepository.ratesOn(date, null, null).first().avgAmt).isEqualTo(3500)
    }

    @Test
    fun `serviceKey 미설정이면 적재하지 않는다(no-op)`() {
        val service =
            FlowerAuctionIngestService(
                FlowerApiClient(RestClient.builder(), properties.copy(serviceKey = "")),
                properties.copy(serviceKey = ""),
                jdbcTemplate,
            )
        service.scheduledIngest()
        assertThat(priceRepository.count()).isZero()
    }
}
