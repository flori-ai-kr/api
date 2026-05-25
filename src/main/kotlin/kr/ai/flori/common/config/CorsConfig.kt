package kr.ai.flori.common.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig {
    @Bean
    fun corsConfigurationSource(properties: CorsProperties): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = properties.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = CORS_MAX_AGE_SECONDS
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }

    private companion object {
        const val CORS_MAX_AGE_SECONDS = 3600L
    }
}
