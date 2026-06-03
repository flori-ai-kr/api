package kr.ai.flori.settings.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

/**
 * 유저 설정(하단바 커스터마이즈) RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * JWT 인증 보호 엔드포인트(bearerAuthJWT). 설정이 없으면 서버 기본 하단바를 반환한다.
 */
class UserPreferenceDocsTest : RestDocsSupport() {
    // ── 1. 유저 설정 조회 ─────────────────────────────────────────────────────

    @Test
    fun `유저 설정 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/preferences") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-preferences-get",
                        responseSchema = "UserPreferencesResponse",
                        tag = "Settings",
                        summary = "유저 설정 조회 (하단바 항목 — 미설정 시 서버 기본값 반환)",
                        responseFields =
                            listOf(
                                fieldWithPath("bottomNavItems")
                                    .type(JsonFieldType.ARRAY)
                                    .description("하단바 항목 키 목록 (예: dashboard, sales, expenses, customers, insights)"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 하단바 항목 변경 ───────────────────────────────────────────────────

    @Test
    fun `하단바 항목 변경 문서화`() {
        val token = signupAndToken()

        mockMvc
            .put("/settings/preferences/bottom-nav") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("items" to listOf("dashboard", "sales", "expenses", "insights")))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-preferences-bottom-nav-update",
                        requestSchema = "UpdateBottomNavRequest",
                        responseSchema = "UserPreferencesResponse",
                        tag = "Settings",
                        summary = "하단바 항목 변경 (upsert — 항목은 4~6개 필수)",
                        requestFields =
                            listOf(
                                fieldWithPath("items")
                                    .type(JsonFieldType.ARRAY)
                                    .description("하단바에 표시할 항목 키 목록 (4~6개 필수)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("bottomNavItems")
                                    .type(JsonFieldType.ARRAY)
                                    .description("변경 후 하단바 항목 키 목록"),
                            ),
                    ),
                )
            }
    }
}
