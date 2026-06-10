package kr.ai.flori.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 서명키·TTL. 서명키는 환경변수에서만 주입(코드/깃에 시크릿 금지).
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val accessTtlSeconds: Long,
    val refreshTtlSeconds: Long,
    // 동시/중복 refresh 멱등 윈도(초). 0 이하면 멱등 비활성. 기본 30초.
    val refreshDedupTtlSeconds: Long = 30,
)
