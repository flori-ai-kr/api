package kr.ai.flori.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * CORS 허용 origin 화이트리스트(앱/웹). 환경변수에서 콤마 구분으로 주입.
 */
@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
