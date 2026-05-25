package kr.ai.flori.settings.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Settings > 카드사/사용자 설정 API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 */
class CardCompanyDocsTest : RestDocsSupport() {
    /** CardCompanyResponse 공통 응답 필드 */
    private val cardCompanyResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.STRING).description("카드사 UUID"),
            fieldWithPath("name").type(JsonFieldType.STRING).description("카드사 이름"),
            fieldWithPath("feeRate")
                .type(JsonFieldType.NUMBER)
                .description("카드 수수료율 (%). 기본 2.0"),
            fieldWithPath("depositDays")
                .type(JsonFieldType.NUMBER)
                .description("입금 소요 영업일. 기본 3"),
            fieldWithPath("isActive")
                .type(JsonFieldType.BOOLEAN)
                .description("활성 여부 (소프트삭제 시 false)"),
        )

    /** 테스트용 카드사 생성 → 생성된 id 문자열 반환 */
    private fun createCardCompany(token: String): String {
        val res =
            mockMvc
                .post("/settings/card-companies") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("name" to "테스트카드-${System.nanoTime()}", "feeRate" to 1.5, "depositDays" to 2))
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 카드사 목록 조회 ────────────────────────────────────────────────────

    @Test
    fun `카드사 목록 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/card-companies") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-card-company-list",
                        tag = "Settings",
                        summary = "카드사 목록 조회 (활성만 반환, 가입 시 9개 시드)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("카드사 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.STRING).description("카드사 UUID"),
                                fieldWithPath("[].name").type(JsonFieldType.STRING).description("카드사 이름"),
                                fieldWithPath("[].feeRate")
                                    .type(JsonFieldType.NUMBER)
                                    .description("카드 수수료율 (%)"),
                                fieldWithPath("[].depositDays")
                                    .type(JsonFieldType.NUMBER)
                                    .description("입금 소요 영업일"),
                                fieldWithPath("[].isActive")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("활성 여부"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 카드사 등록 ─────────────────────────────────────────────────────────

    @Test
    fun `카드사 등록 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/settings/card-companies") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "토스카드",
                            "feeRate" to 1.5,
                            "depositDays" to 2,
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-card-company-create",
                        tag = "Settings",
                        summary = "카드사 등록 (카드사명 중복 불가)",
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .description("카드사 이름 (필수, 계정 내 유일)"),
                                fieldWithPath("feeRate")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("수수료율 % (기본값 2.0)"),
                                fieldWithPath("depositDays")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("입금 소요 영업일 (기본값 3)"),
                            ),
                        responseFields = cardCompanyResponseFields,
                    ),
                )
            }
    }

    // ── 3. 카드사 수정 ─────────────────────────────────────────────────────────

    @Test
    fun `카드사 수수료·입금일 수정 문서화`() {
        val token = signupAndToken()
        val id = createCardCompany(token)

        mockMvc
            .patch("/settings/card-companies/$id") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "feeRate" to 3.0,
                            "depositDays" to 5,
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-card-company-update",
                        tag = "Settings",
                        summary = "카드사 수수료율·입금일 수정 (제공된 필드만 반영)",
                        requestFields =
                            listOf(
                                fieldWithPath("feeRate")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("변경할 수수료율 (%)"),
                                fieldWithPath("depositDays")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("변경할 입금 소요 영업일"),
                            ),
                        responseFields = cardCompanyResponseFields,
                    ),
                )
            }
    }

    // ── 4. 카드사 삭제 ─────────────────────────────────────────────────────────

    @Test
    fun `카드사 삭제 문서화`() {
        val token = signupAndToken()
        val id = createCardCompany(token)

        mockMvc
            .delete("/settings/card-companies/$id") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-card-company-delete",
                        tag = "Settings",
                        summary = "카드사 삭제 (소프트 삭제 — 목록에서 제외)",
                    ),
                )
            }
    }

    // ── 5. 사용자 설정(하단바) 조회 ───────────────────────────────────────────

    @Test
    fun `사용자 설정 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/preferences") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-preferences-get",
                        tag = "Settings",
                        summary = "사용자 설정(하단바) 조회 (기본값: dashboard/sales/expenses/customers/insights)",
                        responseFields =
                            listOf(
                                fieldWithPath("bottomNavItems")
                                    .type(JsonFieldType.ARRAY)
                                    .description("하단바 항목 목록 (4~6개). dashboard | sales | expenses | customers | insights | schedule"),
                            ),
                    ),
                )
            }
    }

    // ── 6. 하단바 항목 변경 ────────────────────────────────────────────────────

    @Test
    fun `하단바 항목 변경 문서화`() {
        val token = signupAndToken()

        mockMvc
            .put("/settings/preferences/bottom-nav") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "items" to listOf("dashboard", "sales", "expenses", "customers"),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-preferences-bottom-nav-update",
                        tag = "Settings",
                        summary = "하단바 항목 변경 (4~6개, 전체 교체)",
                        requestFields =
                            listOf(
                                fieldWithPath("items")
                                    .type(JsonFieldType.ARRAY)
                                    .description("하단바 항목 목록 (4~6개, 필수). dashboard | sales | expenses | customers | insights | schedule"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("bottomNavItems")
                                    .type(JsonFieldType.ARRAY)
                                    .description("변경 후 하단바 항목 목록"),
                            ),
                    ),
                )
            }
    }
}
