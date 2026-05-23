package com.hazel.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * springdoc-openapi 메타 정보. 이 문서가 hazel-app이 읽는 API 계약의 출처다.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun hazelOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Hazel Server API")
                    .description("꽃집 관리 모바일 앱 백엔드 REST API")
                    .version("v1"),
            )
}
