package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.insights.client.KStartupApiClient
import kr.ai.flori.insights.config.KStartupApiProperties
import kr.ai.flori.insights.repository.SupportProgramRepository
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
 * K-Startup 사업공고 적재 서비스 검증 — 라이브 API 대신 MockRestServiceServer 로 응답을 모킹한다.
 * 실제 Zonky PG 에 upsert 해 적재/멱등/정정 동작을 검증한다. 공유 테이블이라 @AfterEach 에서 비운다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SupportProgramIngestServiceTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var programRepository: SupportProgramRepository

    @AfterEach
    fun tearDown() {
        programRepository.deleteAll()
    }

    private val properties =
        KStartupApiProperties(
            baseUrl = "https://apis.data.go.kr/B552735/kisedKstartupService01/getAnnouncementInformation01",
            serviceKey = "deadbeefdeadbeefdeadbeefdeadbeef",
            pages = 1,
            pageSize = 100,
        )

    /** 실제 응답 샘플(사업공고 1건). 금액/날짜는 STRING(yyyyMMdd) 으로 온다. */
    private fun sampleJson(
        pbancSn: Long,
        title: String,
        endDt: String,
    ): String =
        """
        {"currentCount":1,"page":1,"perPage":100,"totalCount":1,"data":[
          {"pbanc_sn":$pbancSn,"biz_pbanc_nm":"$title","intg_pbanc_biz_nm":"창업중심대학",
           "pbanc_ntrp_nm":"중소벤처기업부","supt_biz_clsfc":"사업화","aply_trgt_ctnt":"일반기업",
           "pbanc_ctnt":"공고 내용","pbanc_rcpt_bgng_dt":"20260619","pbanc_rcpt_end_dt":"$endDt",
           "detl_pg_url":"https://www.k-startup.go.kr/web/contents/bizpbanc-ongoing.do?schM=view&pbancSn=$pbancSn"}
        ]}
        """.trimIndent()

    private fun emptyJson(): String = """{"currentCount":0,"page":2,"perPage":100,"totalCount":0,"data":[]}"""

    /** page 1..pages 호출을 stub 하는 클라이언트/모킹 세트(클라이언트와 동일한 URI 빌드). */
    private fun ingestServiceWith(responses: Map<Int, String>): SupportProgramIngestService {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
        for (page in 1..properties.pages) {
            val uri =
                UriComponentsBuilder
                    .fromUriString(properties.baseUrl)
                    .queryParam("ServiceKey", properties.serviceKey)
                    .queryParam("page", page)
                    .queryParam("perPage", properties.pageSize)
                    .queryParam("returnType", "json")
                    .build()
                    .toUriString()
            server
                .expect(ExpectedCount.once(), requestTo(uri))
                .andRespond(withSuccess(responses.getValue(page), MediaType.APPLICATION_JSON))
        }
        val client = KStartupApiClient(builder, properties)
        return SupportProgramIngestService(client, properties, jdbcTemplate)
    }

    @Test
    fun `사업공고를 파싱해 적재하고 yyyyMMdd 날짜를 변환한다`() {
        val service = ingestServiceWith(mapOf(1 to sampleJson(178198, "소상공인 온라인판로 지원사업", "20260708")))

        service.scheduledIngest()

        val rows = programRepository.findAll()
        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.source).isEqualTo("k-startup")
        assertThat(row.sourceId).isEqualTo("178198")
        assertThat(row.title).isEqualTo("소상공인 온라인판로 지원사업")
        assertThat(row.agency).isEqualTo("중소벤처기업부")
        assertThat(row.category).isEqualTo("fund") // 사업화 → fund
        assertThat(row.target).isEqualTo("일반기업")
        assertThat(row.applyStart).isEqualTo(LocalDate.of(2026, 6, 19))
        assertThat(row.applyEnd).isEqualTo(LocalDate.of(2026, 7, 8))
        assertThat(row.sourceUrl).contains("pbancSn=178198")
    }

    @Test
    fun `재실행 시 같은 공고는 멱등 upsert 되고 정정이 반영된다`() {
        ingestServiceWith(mapOf(1 to sampleJson(178198, "소상공인 융자 옛 제목", "20260708"))).scheduledIngest()
        // 같은 pbanc_sn, 제목·마감일만 다른 값으로 재적재.
        ingestServiceWith(mapOf(1 to sampleJson(178198, "소상공인 융자 새 제목", "20260709"))).scheduledIngest()

        assertThat(programRepository.count()).isEqualTo(1)
        val row = programRepository.findAll().first()
        assertThat(row.title).isEqualTo("소상공인 융자 새 제목")
        assertThat(row.applyEnd).isEqualTo(LocalDate.of(2026, 7, 9))
    }

    @Test
    fun `소상공인·화훼와 무관한 공고는 적재하지 않는다`() {
        val service = ingestServiceWith(mapOf(1 to sampleJson(178199, "반도체 소부장 R&D 지원", "20260708")))

        service.scheduledIngest()

        assertThat(programRepository.count()).isZero()
    }

    @Test
    fun `serviceKey 미설정이면 적재하지 않는다(no-op)`() {
        val service =
            SupportProgramIngestService(
                KStartupApiClient(RestClient.builder(), properties.copy(serviceKey = "")),
                properties.copy(serviceKey = ""),
                jdbcTemplate,
            )
        service.scheduledIngest()
        assertThat(programRepository.count()).isZero()
    }
}
