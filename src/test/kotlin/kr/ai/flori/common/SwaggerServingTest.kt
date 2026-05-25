package kr.ai.flori.common

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Swagger UI 서빙 회귀 테스트.
 * - 테스트가 생성한 정적 OpenAPI(`/docs/open-api-3.0.1.json`)는 인증 없이 제공돼야 한다.
 * - springdoc swagger-ui 리소스가 서빙돼야 한다(`api-docs.enabled=false`로 끄면 통째로 비활성됨).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerServingTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `테스트 생성 OpenAPI 정적 스펙은 인증 없이 실제 OpenAPI 내용으로 제공된다`() {
        mockMvc
            .get("/docs/open-api-3.0.1.json")
            .andExpect { status { isOk() } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("\"openapi\"")) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("Flori Server API")) } }
    }

    @Test
    fun `swagger-ui 리소스가 서빙된다 (springdoc 활성)`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }

    @Test
    fun `v3 api-docs는 RestDocs 경로 + JWT bearerAuth 보안스킴 병합본으로 노출된다`() {
        mockMvc
            .get("/v3/api-docs")
            .andExpect { status { isOk() } }
            // Authorize 버튼 — JWT bearer 보안 스킴
            .andExpect { content { string(org.hamcrest.Matchers.containsString("bearerAuth")) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("securitySchemes")) } }
            // RestDocs 생성 경로가 병합돼 있음(스캔이 덮어쓰지 않음)
            .andExpect { content { string(org.hamcrest.Matchers.containsString("\"/sales\"")) } }
    }
}
