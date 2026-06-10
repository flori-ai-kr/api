package kr.ai.flori.common.config

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

/**
 * Swagger UI가 보여주는 OpenAPI 문서를 구성한다(= flori-ai/mobile이 읽는 계약).
 *
 * 문서 본문(paths/schemas)은 RestDocs가 테스트에서 생성한 정적 스펙(static/docs/open-api-3.0.1.json)을
 * 읽어 채우고, 여기에 JWT bearer 보안 스킴 + 전역 SecurityRequirement를 주입한다.
 * → Swagger UI에 Authorize 버튼이 뜨고, 토큰을 한 번 넣으면 보호 엔드포인트를 바로 호출할 수 있다.
 * (정적 스펙 자체엔 보안 스킴이 없으므로 여기서 합친다 — onetime/backend SwaggerConfig 패턴)
 *
 * springdoc 컨트롤러 스캔은 비활성(packages-to-scan = 더미)한다: @Operation을 제거(SPEC-022)했기에
 * 스캔하면 빈약한 경로가 RestDocs 리치 경로를 덮어쓰므로, 문서 출처를 RestDocs 단일로 고정한다.
 */
@Configuration
class OpenApiConfig {
    private val bearerScheme = "bearerAuth"
    private val restDocsSpecPath = "static/docs/open-api-3.0.1.json"

    @Bean
    fun floriOpenApi(): OpenAPI {
        val openApi =
            OpenAPI().info(
                Info()
                    .title("Flori Server API")
                    .description("꽃집 관리 모바일 앱 백엔드 REST API — RestDocs 테스트로 생성·검증된 계약")
                    .version("v1"),
            )

        // RestDocs 생성 스펙에서 paths/components를 가져온다(테스트 환경 등 파일이 없으면 보안 스킴만 노출).
        val resource = ClassPathResource(restDocsSpecPath)
        val components =
            if (resource.exists()) {
                val restDocs = resource.inputStream.use { Json.mapper().readValue(it, OpenAPI::class.java) }
                restDocs.paths?.let { openApi.paths = it }
                restDocs.components ?: Components()
            } else {
                Components()
            }

        components.addSecuritySchemes(
            bearerScheme,
            SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("소셜 로그인/가입 완료(`/auth/oauth/*`, `/auth/register/complete`)로 발급받은 access 토큰. `Authorization: Bearer <token>`"),
        )

        return openApi
            .components(components)
            .addSecurityItem(SecurityRequirement().addList(bearerScheme))
    }
}
