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
)
