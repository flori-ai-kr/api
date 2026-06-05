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
 * Settings > 지출 설정(카테고리/결제방식) API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 */
class ExpenseSettingsDocsTest : RestDocsSupport() {
    /** LabelSettingResponse 공통 응답 필드 */
    private val labelResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("설정 항목 ID"),
            fieldWithPath("value")
                .type(JsonFieldType.STRING)
                .description("식별 값 (영문 snake_case, 지출 기록 시 참조 키)"),
            fieldWithPath("label").type(JsonFieldType.STRING).description("화면 표시 이름"),
            fieldWithPath("sortOrder").type(JsonFieldType.NUMBER).description("정렬 순서"),
        )

    private val labelListResponseFields =
        listOf(
            fieldWithPath("[]").type(JsonFieldType.ARRAY).description("설정 목록"),
            fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("설정 항목 ID"),
            fieldWithPath("[].value").type(JsonFieldType.STRING).description("식별 값 (영문 snake_case)"),
            fieldWithPath("[].label").type(JsonFieldType.STRING).description("화면 표시 이름"),
            fieldWithPath("[].sortOrder").type(JsonFieldType.NUMBER).description("정렬 순서"),
        )

    private val createRequestFields =
        listOf(
            fieldWithPath("label").type(JsonFieldType.STRING).description("화면 표시 이름 (필수)"),
            fieldWithPath("value")
                .type(JsonFieldType.STRING)
                .optional()
                .description("식별 값 (영문 snake_case, 없으면 자동 생성)"),
        )

    private val updateRequestFields =
        listOf(
            fieldWithPath("label").type(JsonFieldType.STRING).description("변경할 화면 표시 이름 (필수)"),
        )

    /** 테스트용 항목 생성 → 생성된 id 반환 (value는 varchar 제한 준수) */
    private fun createItem(
        token: String,
        path: String,
        labelText: String,
        valuePrefix: String,
    ): String {
        val suffix = System.nanoTime().toString().takeLast(6)
        val res =
            mockMvc
                .post(path) {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("label" to labelText, "value" to "${valuePrefix}_$suffix"))
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 지출 카테고리 목록 ─────────────────────────────────────────────────

    @Test
    fun `지출 카테고리 목록 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/expense-categories") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-category-list",
                        responseSchema = "LabelSettingListResponse",
                        tag = "ExpenseSettings",
                        summary = "지출 카테고리 목록 (가입 시 시드 포함)",
                        responseFields = labelListResponseFields,
                    ),
                )
            }
    }

    // ── 2. 지출 카테고리 생성 ─────────────────────────────────────────────────

    @Test
    fun `지출 카테고리 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/settings/expense-categories") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "인건비", "value" to "labor_cost"))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-category-create",
                        requestSchema = "LabelSettingCreateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "ExpenseSettings",
                        summary = "지출 카테고리 생성 (value 중복 불가)",
                        requestFields = createRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 3. 지출 카테고리 수정 ─────────────────────────────────────────────────

    @Test
    fun `지출 카테고리 수정 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/expense-categories", "테스트지출카테고리", "ecat")

        mockMvc
            .put("/settings/expense-categories/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/expense-categories/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "수정된 지출 카테고리"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-category-update",
                        requestSchema = "LabelSettingUpdateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "ExpenseSettings",
                        summary = "지출 카테고리 수정 (label 교체)",
                        pathParameters = listOf(parameterWithName("id").description("지출 카테고리 ID")),
                        requestFields = updateRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 4. 지출 카테고리 삭제 ─────────────────────────────────────────────────

    @Test
    fun `지출 카테고리 삭제 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/expense-categories", "테스트지출카테고리", "ecat")

        mockMvc
            .delete("/settings/expense-categories/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/expense-categories/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-category-delete",
                        tag = "ExpenseSettings",
                        summary = "지출 카테고리 삭제",
                        pathParameters = listOf(parameterWithName("id").description("지출 카테고리 ID")),
                    ),
                )
            }
    }

    // ── 5. 지출 결제방식 목록 ─────────────────────────────────────────────────

    @Test
    fun `지출 결제방식 목록 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/expense-payment-methods") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-payment-method-list",
                        responseSchema = "LabelSettingListResponse",
                        tag = "ExpenseSettings",
                        summary = "지출 결제방식 목록 (가입 시 시드 포함)",
                        responseFields = labelListResponseFields,
                    ),
                )
            }
    }

    // ── 6. 지출 결제방식 생성 ─────────────────────────────────────────────────

    @Test
    fun `지출 결제방식 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/settings/expense-payment-methods") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "법인카드", "value" to "corporate_card"))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-payment-method-create",
                        requestSchema = "LabelSettingCreateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "ExpenseSettings",
                        summary = "지출 결제방식 생성",
                        requestFields = createRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 7. 지출 결제방식 수정 ─────────────────────────────────────────────────

    @Test
    fun `지출 결제방식 수정 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/expense-payment-methods", "테스트지출결제", "epay")

        mockMvc
            .put("/settings/expense-payment-methods/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/expense-payment-methods/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "수정된 지출 결제방식"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-payment-method-update",
                        requestSchema = "LabelSettingUpdateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "ExpenseSettings",
                        summary = "지출 결제방식 수정 (label 교체)",
                        pathParameters = listOf(parameterWithName("id").description("지출 결제방식 ID")),
                        requestFields = updateRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 8. 지출 결제방식 삭제 ─────────────────────────────────────────────────

    @Test
    fun `지출 결제방식 삭제 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/expense-payment-methods", "테스트지출결제", "epay")

        mockMvc
            .delete("/settings/expense-payment-methods/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/expense-payment-methods/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-expense-payment-method-delete",
                        tag = "ExpenseSettings",
                        summary = "지출 결제방식 삭제",
                        pathParameters = listOf(parameterWithName("id").description("지출 결제방식 ID")),
                    ),
                )
            }
    }
}
