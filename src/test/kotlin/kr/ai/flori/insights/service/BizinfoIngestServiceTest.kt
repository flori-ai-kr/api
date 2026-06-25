package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.common.job.JobRunRecorder
import kr.ai.flori.insights.client.BizinfoApiClient
import kr.ai.flori.insights.config.BizinfoApiProperties
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
 * 기업마당(bizinfo) 지원사업 적재 서비스 검증 — 라이브 API 대신 MockRestServiceServer 로 응답을 모킹한다.
 * 실제 Zonky PG 에 upsert 해 적재/멱등/정정/신청기간 분해를 검증한다. 공유 테이블이라 @AfterEach 에서 비운다.
 * K-Startup 과 같은 support_programs 에 source='bizinfo' 로 적재한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class BizinfoIngestServiceTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var programRepository: SupportProgramRepository

    @Autowired
    lateinit var jobRunRecorder: JobRunRecorder

    @AfterEach
    fun tearDown() {
        programRepository.deleteAll()
    }

    private val properties =
        BizinfoApiProperties(
            baseUrl = "https://www.bizinfo.go.kr/uss/rss/bizinfoApi.do",
            crtfcKey = "deadbeefdeadbeefdeadbeefdeadbeef",
            pages = 1,
            pageUnit = 100,
        )

    /** 실제 응답 샘플(공고 1건). jsonArray 래퍼, 신청기간은 "YYYYMMDD ~ YYYYMMDD", 상세 URL 은 상대경로. */
    private fun sampleJson(
        pblancId: String,
        title: String,
        period: String,
        realm: String = "금융",
    ): String =
        """
        {"jsonArray":[
          {"pblancId":"$pblancId","pblancNm":"$title","jrsdInsttNm":"중소벤처기업부",
           "trgetNm":"소상공인","bsnsSumryCn":"정책자금 융자 지원","reqstBeginEndDe":"$period",
           "pldirSportRealmLclasCodeNm":"$realm",
           "pblancUrl":"/web/lay1/bbs/S1T122C128/AS/74/view.do?pblancId=$pblancId"}
        ]}
        """.trimIndent()

    private fun emptyJson(): String = """{"jsonArray":[]}"""

    /** pageIndex 1..pages 호출을 stub 하는 클라이언트/모킹 세트(클라이언트와 동일한 URI 빌드). */
    private fun ingestServiceWith(responses: Map<Int, String>): BizinfoIngestService {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
        for (page in 1..properties.pages) {
            val uri =
                UriComponentsBuilder
                    .fromUriString(properties.baseUrl)
                    .queryParam("crtfcKey", properties.crtfcKey)
                    .queryParam("dataType", "json")
                    .queryParam("pageUnit", properties.pageUnit)
                    .queryParam("pageIndex", page)
                    .build()
                    .toUriString()
            server
                .expect(ExpectedCount.once(), requestTo(uri))
                .andRespond(withSuccess(responses.getValue(page), MediaType.APPLICATION_JSON))
        }
        val client = BizinfoApiClient(builder, properties)
        return BizinfoIngestService(client, properties, jdbcTemplate, jobRunRecorder)
    }

    @Test
    fun `공고를 파싱해 적재하고 신청기간을 분해한다`() {
        val service = ingestServiceWith(mapOf(1 to sampleJson("PBLN_000000000099999", "소상공인 정책자금 융자", "20260601 ~ 20260630")))

        service.runIngest()

        val rows = programRepository.findAll()
        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.source).isEqualTo("bizinfo")
        assertThat(row.sourceId).isEqualTo("PBLN_000000000099999")
        assertThat(row.title).isEqualTo("소상공인 정책자금 융자")
        assertThat(row.agency).isEqualTo("중소벤처기업부")
        assertThat(row.category).isEqualTo("fund") // 금융 → fund
        assertThat(row.target).isEqualTo("소상공인")
        assertThat(row.applyStart).isEqualTo(LocalDate.of(2026, 6, 1))
        assertThat(row.applyEnd).isEqualTo(LocalDate.of(2026, 6, 30))
        assertThat(row.sourceUrl).isEqualTo(
            "https://www.bizinfo.go.kr/web/lay1/bbs/S1T122C128/AS/74/view.do?pblancId=PBLN_000000000099999",
        )
    }

    @Test
    fun `재실행 시 같은 공고는 멱등 upsert 되고 정정이 반영된다`() {
        ingestServiceWith(mapOf(1 to sampleJson("PBLN_1", "옛 제목", "20260601 ~ 20260630"))).runIngest()
        ingestServiceWith(mapOf(1 to sampleJson("PBLN_1", "새 제목", "20260601 ~ 20260701"))).runIngest()

        assertThat(programRepository.count()).isEqualTo(1)
        val row = programRepository.findAll().first()
        assertThat(row.title).isEqualTo("새 제목")
        assertThat(row.applyEnd).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun `상시(신청기간 비어있음) 공고는 신청기간이 null 이다`() {
        val service = ingestServiceWith(mapOf(1 to sampleJson("PBLN_2", "상시 모집 공고", "")))

        service.runIngest()

        val row = programRepository.findAll().first()
        assertThat(row.applyStart).isNull()
        assertThat(row.applyEnd).isNull()
    }

    @Test
    fun `소상공인·화훼와 무관한 공고는 적재하지 않는다`() {
        val irrelevant =
            """
            {"jsonArray":[
              {"pblancId":"PBLN_9","pblancNm":"반도체 소부장 R&D 지원","jrsdInsttNm":"산업통상자원부",
               "trgetNm":"중소기업","bsnsSumryCn":"기술개발 지원","reqstBeginEndDe":"20260601 ~ 20260630",
               "pldirSportRealmLclasCodeNm":"기술","pblancUrl":"/x"}
            ]}
            """.trimIndent()
        val service = ingestServiceWith(mapOf(1 to irrelevant))

        service.runIngest()

        assertThat(programRepository.count()).isZero()
    }

    @Test
    fun `crtfcKey 미설정이면 적재하지 않는다(no-op)`() {
        val service =
            BizinfoIngestService(
                BizinfoApiClient(RestClient.builder(), properties.copy(crtfcKey = "")),
                properties.copy(crtfcKey = ""),
                jdbcTemplate,
                jobRunRecorder,
            )
        service.runIngest()
        assertThat(programRepository.count()).isZero()
    }
}
