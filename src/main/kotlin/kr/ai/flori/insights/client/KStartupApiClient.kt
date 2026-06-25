package kr.ai.flori.insights.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kr.ai.flori.insights.config.KStartupApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

// ─── 창업진흥원 K-Startup 사업공고 OpenAPI(data.go.kr) 계약 ────────────────
// getAnnouncementInformation01: 사업공고 목록. 응답은 top-level {data:[...], page, perPage, totalCount}.
// 필드는 snake_case 라 @JsonProperty 로 매핑. 빈 페이지: data=[].
// 인증키 파라미터명은 `ServiceKey`(대문자 S) — data.go.kr 케이스 구분.
// license: 이용허락범위 제한 없음(상업 OK).

@JsonIgnoreProperties(ignoreUnknown = true)
data class KStartupResponse(
    val data: List<KStartupAnnouncement> = emptyList(),
    val page: Int? = null,
    val perPage: Int? = null,
    val totalCount: Int? = null,
    val currentCount: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KStartupAnnouncement(
    /** 공고 일련번호(고유) → source_id. */
    @JsonProperty("pbanc_sn") val pbancSn: Long? = null,
    /** 지원사업 공고명 → title. */
    @JsonProperty("biz_pbanc_nm") val bizPbancNm: String? = null,
    /** 통합공고 사업명(공고명 폴백). */
    @JsonProperty("intg_pbanc_biz_nm") val intgPbancBizNm: String? = null,
    /** 공고기관명 → agency. */
    @JsonProperty("pbanc_ntrp_nm") val pbancNtrpNm: String? = null,
    /** 지원분야(사업화/마케팅/교육 등) → category 매핑 원천. */
    @JsonProperty("supt_biz_clsfc") val suptBizClsfc: String? = null,
    /** 신청대상 내용 → target. */
    @JsonProperty("aply_trgt_ctnt") val aplyTrgtCtnt: String? = null,
    /** 공고 내용 → summary. */
    @JsonProperty("pbanc_ctnt") val pbancCtnt: String? = null,
    /** 접수 시작일(yyyyMMdd) → apply_start. */
    @JsonProperty("pbanc_rcpt_bgng_dt") val pbancRcptBgngDt: String? = null,
    /** 접수 마감일(yyyyMMdd) → apply_end. */
    @JsonProperty("pbanc_rcpt_end_dt") val pbancRcptEndDt: String? = null,
    /** 상세페이지 URL → source_url. */
    @JsonProperty("detl_pg_url") val detlPgUrl: String? = null,
)

/**
 * K-Startup 사업공고 HTTP 클라이언트.
 *
 * RestClient.Builder 를 주입받아(테스트에서 MockRestServiceServer 바인딩 가능, 라이브 호출 금지).
 * 적재 cron(SupportProgramIngestService)만 호출하며, serviceKey 는 properties(env)에서 온다.
 */
@Component
class KStartupApiClient(
    builder: RestClient.Builder,
    private val properties: KStartupApiProperties,
) {
    private val restClient: RestClient = builder.build()

    /**
     * 사업공고 단일 페이지 조회(최신순). 호출 실패는 호출자(적재 서비스)가 페이지 단위로 격리·로깅한다.
     */
    fun fetch(
        page: Int,
        perPage: Int,
    ): KStartupResponse {
        val uri =
            UriComponentsBuilder
                .fromUriString(properties.baseUrl)
                .queryParam("ServiceKey", properties.serviceKey)
                .queryParam("page", page)
                .queryParam("perPage", perPage)
                .queryParam("returnType", "json")
                .build()
                .toUriString()
        return restClient
            .get()
            .uri(uri)
            .retrieve()
            .body(KStartupResponse::class.java) ?: KStartupResponse()
    }
}
