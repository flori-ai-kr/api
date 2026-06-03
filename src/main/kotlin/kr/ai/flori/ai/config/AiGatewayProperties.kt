package kr.ai.flori.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AI 게이트웨이 설정. ai-server(FastAPI)는 내부망 전용 — base-url/내부키는 env로만 주입.
 * 게이트웨이가 web↔ai-server 사이에서 모든 호출을 중개·로깅한다.
 */
@ConfigurationProperties(prefix = "ai.gateway")
data class AiGatewayProperties(
    /** ai-server base URL (예: http://localhost:8000). 빈 값이면 AI 기능 비활성. */
    val baseUrl: String = "",
    /** 게이트웨이↔ai-server 신뢰용 내부키(X-Internal-Key). */
    val internalKey: String = "",
    /** 품질 경로 기본 모델(채팅·OCR·proactive). ai-server LiteLLM alias와 일치. */
    val chatModel: String = "claude-sonnet-4-6",
    /** 유저별 일일 AI 호출 캡(메시지+제안+OCR 합산 기준). */
    val usageCapPerDay: Int = 500,
    /** OCR 쓰기 제안 TTL(초). 이 시간이 지나면 확인 불가(만료). */
    val proposalTtlSeconds: Long = 86400,
    val connectTimeoutMs: Int = 3000,
    val readTimeoutMs: Int = 60000,
)
