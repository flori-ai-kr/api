package kr.ai.flori.insights.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 기업마당(bizinfo) 지원사업 OpenAPI 설정. crtfcKey 는 env로만 주입(커밋 금지).
 *
 * 소진공·지자체·중기부 공고를 통합 집계하는 단일 소스다. 지원사업 공고를 적재하는
 * cron(BizinfoIngestService)이 source='bizinfo' 로 support_programs 에 적재한다.
 * crtfcKey 가 빈 값이면 cron 은 no-op(경고 로그) — 키 없는 dev 환경도 부팅 가능.
 *
 * 주의: bizinfo 인증키 발급은 기업마당 자체("정책정보 개방")에서 한다(data.go.kr 아님).
 */
@ConfigurationProperties(prefix = "flori.bizinfo-api")
data class BizinfoApiProperties(
    /** bizinfoApi.do operation URL. 빈 값이면 적재 비활성. */
    val baseUrl: String = "",
    /** 기업마당 발급 인증키(crtfcKey). 빈 값이면 적재 cron no-op. */
    val crtfcKey: String = "",
    /** 적재 cron 스케줄(KST cron 식). */
    val cron: String = "0 0 8 * * *",
    /** 매 실행 시 조회할 페이지 수(최신순 pageUnit*pages 건). 소상공인 관련도 필터로 적재량이 줄어 넉넉히 잡는다. */
    val pages: Int = 10,
    /** 페이지당 결과 수(pageUnit). */
    val pageUnit: Int = 100,
    /** 검색 해시태그 필터(지역·키워드, 쉼표구분). 빈 값이면 전체. */
    val hashtags: String = "",
)
