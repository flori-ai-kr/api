package kr.ai.flori.insights.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * aT 화훼유통정보 OpenAPI(f001) 설정. serviceKey 는 env로만 주입(커밋 금지).
 *
 * 단일 시장(aT 양재) 일별 경락가를 적재하는 cron(FlowerAuctionIngestService)이 사용한다.
 * service-key 가 빈 값이면 cron 은 no-op(경고 로그) — 키 없는 dev 환경도 부팅 가능.
 */
@ConfigurationProperties(prefix = "flori.flower-api")
data class FlowerApiProperties(
    /** f001 base URL(예: https://flower.at.or.kr/api/returnData.api). 빈 값이면 적재 비활성. */
    val baseUrl: String = "",
    /** aT 발급 serviceKey(32 hex). 빈 값이면 적재 cron no-op. */
    val serviceKey: String = "",
    /** 적재 cron 스케줄(KST cron 식). */
    val cron: String = "0 30 6 * * *",
    /** 매 실행 시 거슬러 적재할 일수(정산 지연/누락일 커버, 멱등). */
    val backfillDays: Int = 3,
    /** f001 페이지당 행 수(countPerPage). */
    val pageSize: Int = 1000,
)
