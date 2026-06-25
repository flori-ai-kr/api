package kr.ai.flori.insights.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kr.ai.flori.insights.config.BizinfoApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

// ─── 기업마당(bizinfo) 지원사업 OpenAPI 계약 ───────────────────────────────
// bizinfoApi.do: 최신 지원사업 공고 목록. 응답은 {"jsonArray":[...]} 래퍼(빈 페이지: jsonArray=[]).
// 필드는 eGov 네이밍(camelCase 변형) 이라 @JsonProperty 로 매핑.
// 인증키 파라미터명은 `crtfcKey`. 상세 URL(pblancUrl)은 상대경로 → 적재 시 호스트를 붙인다.

@JsonIgnoreProperties(ignoreUnknown = true)
data class BizinfoResponse(
    val jsonArray: List<BizinfoItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BizinfoItem(
    /** 공고 ID(고유) → source_id. */
    @JsonProperty("pblancId") val pblancId: String? = null,
    /** 공고명 → title. */
    @JsonProperty("pblancNm") val pblancNm: String? = null,
    /** 소관기관명 → agency. */
    @JsonProperty("jrsdInsttNm") val jrsdInsttNm: String? = null,
    /** 지원대상 → target. */
    @JsonProperty("trgetNm") val trgetNm: String? = null,
    /** 사업개요 → summary. */
    @JsonProperty("bsnsSumryCn") val bsnsSumryCn: String? = null,
    /** 신청기간("YYYYMMDD ~ YYYYMMDD" / 상시·공백) → apply_start/apply_end. */
    @JsonProperty("reqstBeginEndDe") val reqstBeginEndDe: String? = null,
    /** 지원분야 대분류명(금융/수출/창업 등) → category 매핑 원천. */
    @JsonProperty("pldirSportRealmLclasCodeNm") val pldirSportRealmLclasCodeNm: String? = null,
    /** 상세페이지 URL(상대경로) → source_url(호스트 결합). */
    @JsonProperty("pblancUrl") val pblancUrl: String? = null,
)

/**
 * 기업마당 지원사업 HTTP 클라이언트.
 *
 * RestClient.Builder 를 주입받아(테스트에서 MockRestServiceServer 바인딩 가능, 라이브 호출 금지).
 * 적재 cron(BizinfoIngestService)만 호출하며, crtfcKey 는 properties(env)에서 온다.
 */
@Component
class BizinfoApiClient(
    builder: RestClient.Builder,
    private val properties: BizinfoApiProperties,
) {
    private val restClient: RestClient = builder.build()

    /**
     * 지원사업 단일 페이지 조회(최신순). 호출 실패는 호출자(적재 서비스)가 페이지 단위로 격리·로깅한다.
     */
    fun fetch(
        pageIndex: Int,
        pageUnit: Int,
    ): BizinfoResponse {
        val builder =
            UriComponentsBuilder
                .fromUriString(properties.baseUrl)
                .queryParam("crtfcKey", properties.crtfcKey)
                .queryParam("dataType", "json")
                .queryParam("pageUnit", pageUnit)
                .queryParam("pageIndex", pageIndex)
        if (properties.hashtags.isNotBlank()) {
            builder.queryParam("hashtags", properties.hashtags)
        }
        return restClient
            .get()
            .uri(builder.build().toUriString())
            .retrieve()
            .body(BizinfoResponse::class.java) ?: BizinfoResponse()
    }
}
