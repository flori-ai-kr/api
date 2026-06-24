package kr.ai.flori.insights.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 네이버 검색 OpenAPI(뉴스) 설정. clientId/clientSecret 은 env로만 주입(커밋 금지).
 *
 * 트렌드·뉴스 기사를 적재하는 cron(TrendIngestService)이 카테고리별 검색어로 뉴스를 수집해 trend_articles 에 적재한다.
 * 네이버 뉴스 RSS 는 폐지돼(2020) 검색 OpenAPI 로 대체한다 — 인증은 헤더(X-Naver-Client-Id/Secret).
 * clientId/clientSecret 이 빈 값이면 cron 은 no-op(경고 로그) — 키 없는 dev 환경도 부팅 가능.
 */
@ConfigurationProperties(prefix = "flori.naver-search-api")
data class NaverSearchApiProperties(
    /** 검색 OpenAPI base URL(news.json 의 부모 경로). 빈 값이면 적재 비활성. */
    val baseUrl: String = "https://openapi.naver.com/v1/search",
    /** 네이버 개발자센터 애플리케이션 Client ID. 빈 값이면 적재 cron no-op. */
    val clientId: String = "",
    /** 네이버 개발자센터 애플리케이션 Client Secret. 빈 값이면 적재 cron no-op. */
    val clientSecret: String = "",
    /** 적재 cron 스케줄(KST cron 식). */
    val cron: String = "0 30 7 * * *",
    /** 검색어당 가져올 뉴스 건수(display, 1~100). */
    val display: Int = 20,
)
