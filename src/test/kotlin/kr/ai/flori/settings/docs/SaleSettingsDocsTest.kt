package kr.ai.flori.settings.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Settings > 매출 설정(카테고리/결제방식) API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * 가입 시 매출 카테고리 11개, 결제방식이 시드로 삽입된다.
 */
class SaleSettingsDocsTest : RestDocsSupport() {
    /** LabelSettingResponse 공통 응답 필드 */
    private val labelResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("설정 항목 ID"),
            fieldWithPath("value")
                .type(JsonFieldType.STRING)
                .description("식별 값 (영문 snake_case, 매출 기록 시 참조 키)"),
            fieldWithPath("label").type(JsonFieldType.STRING).description("화면 표시 이름"),
            fieldWithPath("color").type(JsonFieldType.STRING).description("표시 색상 (hex)"),
            fieldWithPath("sortOrder").type(JsonFieldType.NUMBER).description("정렬 순서"),
        )

    /** 테스트용 매출 카테고리 생성 → 생성된 id 반환 (value는 varchar(20) 제한 준수) */
    private fun createCategory(token: String): String {
        val suffix = System.nanoTime().toString().takeLast(6)
        val res =
            mockMvc
                .post("/settings/sale-categories") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "label" to "테스트카테고리",
                                "color" to "#aabbcc",
                                "value" to "tcat_$suffix",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    /** 테스트용 결제방식 생성 → 생성된 id 반환 (value는 varchar(20) 제한 준수) */
    private fun createPayment(token: String): String {
        val suffix = System.nanoTime().toString().takeLast(6)
        val res =
            mockMvc
                .post("/settings/payment-methods") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "label" to "테스트결제",
                                "color" to "#112233",
                                "value" to "tpay_$suffix",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 매출 카테고리 목록 ─────────────────────────────────────────────────

    @Test
    fun `매출 카테고리 목록 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/sale-categories") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-category-list",
                        responseSchema = "LabelSettingListResponse",
                        tag = "SaleSettings",
                        summary = "매출 카테고리 목록 (가입 시 11개 시드)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("카테고리 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("설정 항목 ID"),
                                fieldWithPath("[].value")
                                    .type(JsonFieldType.STRING)
                                    .description("식별 값 (영문 snake_case)"),
                                fieldWithPath("[].label")
                                    .type(JsonFieldType.STRING)
                                    .description("화면 표시 이름"),
                                fieldWithPath("[].color")
                                    .type(JsonFieldType.STRING)
                                    .description("표시 색상 (hex)"),
                                fieldWithPath("[].sortOrder")
                                    .type(JsonFieldType.NUMBER)
                                    .description("정렬 순서"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 매출 카테고리 생성 ─────────────────────────────────────────────────

    @Test
    fun `매출 카테고리 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/settings/sale-categories") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "label" to "발렌타인 꽃다발",
                            "color" to "#e53935",
                            "value" to "valentine_bouquet",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-category-create",
                        requestSchema = "LabelSettingCreateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 카테고리 생성 (value 중복 불가)",
                        requestFields =
                            listOf(
                                fieldWithPath("label")
                                    .type(JsonFieldType.STRING)
                                    .description("화면 표시 이름 (필수)"),
                                fieldWithPath("color")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("표시 색상 hex (없으면 기본값 적용)"),
                                fieldWithPath("value")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("식별 값 (영문 snake_case, 없으면 자동 생성)"),
                            ),
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 3. 매출 카테고리 수정 ─────────────────────────────────────────────────

    @Test
    fun `매출 카테고리 수정 문서화`() {
        val token = signupAndToken()
        val id = createCategory(token)

        mockMvc
            .put("/settings/sale-categories/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/sale-categories/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "label" to "수정된 카테고리",
                            "color" to "#ffffff",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-category-update",
                        requestSchema = "LabelSettingUpdateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 카테고리 수정 (label·color 전체 교체)",
                        pathParameters = listOf(parameterWithName("id").description("매출 카테고리 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("label")
                                    .type(JsonFieldType.STRING)
                                    .description("변경할 화면 표시 이름 (필수)"),
                                fieldWithPath("color")
                                    .type(JsonFieldType.STRING)
                                    .description("변경할 색상 hex (필수)"),
                            ),
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 4. 매출 카테고리 삭제 ─────────────────────────────────────────────────

    @Test
    fun `매출 카테고리 삭제 문서화`() {
        val token = signupAndToken()
        val id = createCategory(token)

        mockMvc
            .delete("/settings/sale-categories/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/sale-categories/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-category-delete",
                        tag = "SaleSettings",
                        summary = "매출 카테고리 삭제",
                        pathParameters = listOf(parameterWithName("id").description("매출 카테고리 ID")),
                    ),
                )
            }
    }

    // ── 5. 매출 결제방식 목록 ─────────────────────────────────────────────────

    @Test
    fun `매출 결제방식 목록 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/payment-methods") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-payment-method-list",
                        responseSchema = "LabelSettingListResponse",
                        tag = "SaleSettings",
                        summary = "매출 결제방식 목록 (가입 시 시드 포함)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("결제방식 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("설정 항목 ID"),
                                fieldWithPath("[].value")
                                    .type(JsonFieldType.STRING)
                                    .description("식별 값 (영문 snake_case)"),
                                fieldWithPath("[].label")
                                    .type(JsonFieldType.STRING)
                                    .description("화면 표시 이름"),
                                fieldWithPath("[].color")
                                    .type(JsonFieldType.STRING)
                                    .description("표시 색상 (hex)"),
                                fieldWithPath("[].sortOrder")
                                    .type(JsonFieldType.NUMBER)
                                    .description("정렬 순서"),
                            ),
                    ),
                )
            }
    }

    // ── 6. 매출 결제방식 생성 ─────────────────────────────────────────────────

    @Test
    fun `매출 결제방식 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/settings/payment-methods") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "label" to "계좌이체",
                            "color" to "#1565c0",
                            "value" to "bank_transfer",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-payment-method-create",
                        requestSchema = "LabelSettingCreateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 결제방식 생성",
                        requestFields =
                            listOf(
                                fieldWithPath("label")
                                    .type(JsonFieldType.STRING)
                                    .description("화면 표시 이름 (필수)"),
                                fieldWithPath("color")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("표시 색상 hex"),
                                fieldWithPath("value")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("식별 값 (영문 snake_case, 없으면 자동 생성)"),
                            ),
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 7. 매출 결제방식 수정 ─────────────────────────────────────────────────

    @Test
    fun `매출 결제방식 수정 문서화`() {
        val token = signupAndToken()
        val id = createPayment(token)

        mockMvc
            .put("/settings/payment-methods/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/payment-methods/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "label" to "수정된 결제방식",
                            "color" to "#000000",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-payment-method-update",
                        requestSchema = "LabelSettingUpdateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 결제방식 수정 (label·color 전체 교체)",
                        pathParameters = listOf(parameterWithName("id").description("결제방식 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("label")
                                    .type(JsonFieldType.STRING)
                                    .description("변경할 화면 표시 이름 (필수)"),
                                fieldWithPath("color")
                                    .type(JsonFieldType.STRING)
                                    .description("변경할 색상 hex (필수)"),
                            ),
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 8. 매출 결제방식 삭제 ─────────────────────────────────────────────────

    @Test
    fun `매출 결제방식 삭제 문서화`() {
        val token = signupAndToken()
        val id = createPayment(token)

        mockMvc
            .delete("/settings/payment-methods/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/payment-methods/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-payment-method-delete",
                        tag = "SaleSettings",
                        summary = "매출 결제방식 삭제",
                        pathParameters = listOf(parameterWithName("id").description("결제방식 ID")),
                    ),
                )
            }
    }
}
