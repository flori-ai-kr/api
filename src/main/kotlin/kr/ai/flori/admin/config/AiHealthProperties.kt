package kr.ai.flori.admin.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AI 헬스 프록시 타깃. 미설정(빈 문자열) 시 해당 타깃은 건너뛴다.
 * - serverUrl: ai-server 헬스 (예: https://dev-ai.flori.ai.kr/health)
 * - litellmUrl: litellm liveliness (예: http://litellm:4000/health/liveliness)
 */
@ConfigurationProperties(prefix = "ai.health")
data class AiHealthProperties(
    val serverUrl: String = "",
    val litellmUrl: String = "",
)
