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
 * Settings > 매출 설정(카테고리/결제방식/채널) API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * 가입 시 매출 카테고리 11개, 결제방식 4개, 채널 5개가 시드로 삽입된다.
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
                        responseFields = labelListResponseFields,
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
                content = json(mapOf("label" to "발렌타인 꽃다발", "value" to "valentine_bouquet"))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-category-create",
                        requestSchema = "LabelSettingCreateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 카테고리 생성 (value 중복 불가)",
                        requestFields = createRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 3. 매출 카테고리 수정 ─────────────────────────────────────────────────

    @Test
    fun `매출 카테고리 수정 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/sale-categories", "테스트카테고리", "tcat")

        mockMvc
            .put("/settings/sale-categories/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/sale-categories/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "수정된 카테고리"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-category-update",
                        requestSchema = "LabelSettingUpdateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 카테고리 수정 (label 교체)",
                        pathParameters = listOf(parameterWithName("id").description("매출 카테고리 ID")),
                        requestFields = updateRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 4. 매출 카테고리 삭제 ─────────────────────────────────────────────────

    @Test
    fun `매출 카테고리 삭제 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/sale-categories", "테스트카테고리", "tcat")

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
                        summary = "매출 결제방식 목록 (가입 시 4개 시드)",
                        responseFields = labelListResponseFields,
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
                content = json(mapOf("label" to "계좌이체", "value" to "bank_transfer"))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-payment-method-create",
                        requestSchema = "LabelSettingCreateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 결제방식 생성",
                        requestFields = createRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 7. 매출 결제방식 수정 ─────────────────────────────────────────────────

    @Test
    fun `매출 결제방식 수정 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/payment-methods", "테스트결제", "tpay")

        mockMvc
            .put("/settings/payment-methods/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/payment-methods/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "수정된 결제방식"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-payment-method-update",
                        requestSchema = "LabelSettingUpdateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 결제방식 수정 (label 교체)",
                        pathParameters = listOf(parameterWithName("id").description("결제방식 ID")),
                        requestFields = updateRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 8. 매출 결제방식 삭제 ─────────────────────────────────────────────────

    @Test
    fun `매출 결제방식 삭제 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/payment-methods", "테스트결제", "tpay")

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

    // ── 9. 매출 채널 목록 ─────────────────────────────────────────────────────

    @Test
    fun `매출 채널 목록 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/settings/sale-channels") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-channel-list",
                        responseSchema = "LabelSettingListResponse",
                        tag = "SaleSettings",
                        summary = "매출 채널(유입 경로) 목록 (가입 시 5개 시드)",
                        responseFields = labelListResponseFields,
                    ),
                )
            }
    }

    // ── 10. 매출 채널 생성 ────────────────────────────────────────────────────

    @Test
    fun `매출 채널 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/settings/sale-channels") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "인스타그램", "value" to "instagram"))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-channel-create",
                        requestSchema = "LabelSettingCreateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 채널 생성 (value 중복 불가)",
                        requestFields = createRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 11. 매출 채널 수정 ────────────────────────────────────────────────────

    @Test
    fun `매출 채널 수정 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/sale-channels", "테스트채널", "tchan")

        mockMvc
            .put("/settings/sale-channels/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/sale-channels/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("label" to "수정된 채널"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-channel-update",
                        requestSchema = "LabelSettingUpdateRequest",
                        responseSchema = "LabelSettingResponse",
                        tag = "SaleSettings",
                        summary = "매출 채널 수정 (label 교체)",
                        pathParameters = listOf(parameterWithName("id").description("채널 ID")),
                        requestFields = updateRequestFields,
                        responseFields = labelResponseFields,
                    ),
                )
            }
    }

    // ── 12. 매출 채널 삭제 ────────────────────────────────────────────────────

    @Test
    fun `매출 채널 삭제 문서화`() {
        val token = signupAndToken()
        val id = createItem(token, "/settings/sale-channels", "테스트채널", "tchan")

        mockMvc
            .delete("/settings/sale-channels/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/settings/sale-channels/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "settings-sale-channel-delete",
                        tag = "SaleSettings",
                        summary = "매출 채널 삭제",
                        pathParameters = listOf(parameterWithName("id").description("채널 ID")),
                    ),
                )
            }
    }
}
