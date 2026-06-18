package kr.ai.flori.insights.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kr.ai.flori.insights.config.FlowerApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate

// ─── aT 화훼유통정보 OpenAPI(f001) 계약 ─────────────────────────────
// 단일 시장(aT 양재) 일별 경락가. 시장/법인 구분 없음.
// 응답 금액/수량은 STRING 으로 온다 — 숫자 파싱은 적재 서비스가 담당.
// 빈 날: response.numOfRows="0", items=[]. license: 이용허락범위 = "제작자 표시".

@JsonIgnoreProperties(ignoreUnknown = true)
data class F001Response(
    val response: F001Body? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class F001Body(
    val resultCd: String? = null,
    val resultMsg: String? = null,
    val numOfRows: String? = null,
    val items: List<F001Item> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class F001Item(
    val saleDate: String? = null,
    val flowerGubn: String? = null,
    val pumName: String? = null,
    val goodName: String? = null,
    val lvNm: String? = null,
    val maxAmt: String? = null,
    val minAmt: String? = null,
    val avgAmt: String? = null,
    val totAmt: String? = null,
    val totQty: String? = null,
)

/**
 * aT 화훼유통정보 f001 HTTP 클라이언트(단일 시장, aT 양재).
 *
 * RestClient.Builder 를 주입받아(OAuth 클라이언트와 동일) 테스트에서 MockRestServiceServer 로 바인딩 가능
 * (라이브 호출 금지). 적재 cron(FlowerAuctionIngestService)만 호출하며, serviceKey 는 properties(env)에서 온다.
 */
@Component
class FlowerApiClient(
    builder: RestClient.Builder,
    private val properties: FlowerApiProperties,
) {
    private val restClient: RestClient = builder.build()

    /**
     * f001 단일 페이지 조회. baseDate(YYYY-MM-DD), flowerGubn(1~4), currentPage 로 페이징.
     * 호출 실패는 호출자(적재 서비스)가 per-day/per-gubn 단위로 격리·로깅한다.
     */
    fun fetch(
        baseDate: LocalDate,
        flowerGubn: String,
        currentPage: Int,
    ): F001Response {
        val uri =
            UriComponentsBuilder
                .fromUriString(properties.baseUrl)
                .queryParam("kind", "f001")
                .queryParam("serviceKey", properties.serviceKey)
                .queryParam("baseDate", baseDate.toString())
                .queryParam("flowerGubn", flowerGubn)
                .queryParam("dataType", "json")
                .queryParam("countPerPage", properties.pageSize)
                .queryParam("currentPage", currentPage)
                .build()
                .toUriString()
        return restClient
            .get()
            .uri(uri)
            .retrieve()
            .body(F001Response::class.java) ?: F001Response()
    }
}
