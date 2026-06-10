package kr.ai.flori.common.config

import kr.ai.flori.common.log.LoggingInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 접근 로그 인터셉터를 모든 경로에 등록한다.
 * 노이즈 경로(헬스/Swagger/OpenAPI/정적 문서/파비콘)는 접근 로그에서 제외한다.
 */
@Configuration
class WebConfig(
    private val loggingInterceptor: LoggingInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(loggingInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/actuator/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/docs/**",
                "/favicon.ico",
            )
    }
}
