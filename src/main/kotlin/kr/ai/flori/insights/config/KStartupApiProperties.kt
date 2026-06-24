package kr.ai.flori.insights.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 창업진흥원 K-Startup 사업공고 OpenAPI(data.go.kr) 설정. serviceKey 는 env로만 주입(커밋 금지).
 *
 * 지원사업 공고를 적재하는 cron(SupportProgramIngestService)이 사용한다.
 * service-key 가 빈 값이면 cron 은 no-op(경고 로그) — 키 없는 dev 환경도 부팅 가능.
 *
 * 주의: data.go.kr 파라미터명은 대소문자 구분 — 인증키 키는 `ServiceKey`(대문자 S)다.
 */
@ConfigurationProperties(prefix = "flori.kstartup-api")
data class KStartupApiProperties(
    /** getAnnouncementInformation01 operation URL. 빈 값이면 적재 비활성. */
    val baseUrl: String = "",
    /** data.go.kr 발급 일반 인증키(Decoding 원본). 빈 값이면 적재 cron no-op. */
    val serviceKey: String = "",
    /** 적재 cron 스케줄(KST cron 식). 경매(06:30) 직후 06:31. */
    val cron: String = "0 31 6 * * *",
    /** 매 실행 시 조회할 페이지 수(최신순 perPage*pages 건). 소상공인 관련도 필터로 적재량이 줄어 넉넉히 잡는다. */
    val pages: Int = 10,
    /** 페이지당 결과 수(perPage). */
    val pageSize: Int = 100,
)
