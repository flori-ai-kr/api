package kr.ai.flori.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * springdoc-openapi 메타 정보. 이 문서가 flori-ai/mobile이 읽는 API 계약의 출처다.
 *
 * JWT bearer 보안 스킴을 전역 등록 → Swagger UI에 Authorize 버튼이 생기고,
 * 모든 보호 엔드포인트가 Authorization 헤더를 요구함을 계약에 노출한다.
 * (인증 없이 호출 가능한 auth·webhooks 등 공개 경로는 SecurityConfig가 실제 접근을 허용)
 */
@Configuration
class OpenApiConfig {
    private val bearerScheme = "bearerAuth"

    @Bean
    fun floriOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Flori Server API")
                    .description("꽃집 관리 모바일 앱 백엔드 REST API")
                    .version("v1"),
            ).components(
                Components().addSecuritySchemes(
                    bearerScheme,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("`/auth/login`으로 발급받은 access 토큰. `Authorization: Bearer <token>`"),
                ),
            ).addSecurityItem(SecurityRequirement().addList(bearerScheme))
}
