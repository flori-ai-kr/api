package kr.ai.flori.expenses.docs

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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/**
 * Expenses API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * totalAmount 는 단가(unitPrice) × 수량(quantity) 로 서버가 계산하는 SSOT 값이다.
 */
class ExpenseDocsTest : RestDocsSupport() {
    /** ExpenseResponse 공통 응답 필드 — 단건 조회/생성/수정에서 재사용 */
    private val expenseResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("지출 ID"),
            fieldWithPath("date").type(JsonFieldType.STRING).description("지출 발생일 (yyyy-MM-dd)"),
            fieldWithPath("itemName").type(JsonFieldType.STRING).description("물품명"),
            fieldWithPath("category").type(JsonFieldType.STRING).description("지출 카테고리"),
            fieldWithPath("unitPrice").type(JsonFieldType.NUMBER).description("단가(원)"),
            fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("수량"),
            fieldWithPath("totalAmount")
                .type(JsonFieldType.NUMBER)
                .description("[서버 계산 SSOT] 총액 = 단가 × 수량"),
            fieldWithPath("paymentMethod")
                .type(JsonFieldType.STRING)
                .description("결제방식. cash | card | transfer | naverpay | kakaopay"),
            fieldWithPath("cardCompany")
                .type(JsonFieldType.STRING)
                .optional()
                .description("카드사 (카드 결제 시)"),
            fieldWithPath("vendor")
                .type(JsonFieldType.STRING)
                .optional()
                .description("거래처 (null 가능)"),
            fieldWithPath("memo")
                .type(JsonFieldType.STRING)
                .optional()
                .description("비고 (null 가능)"),
            fieldWithPath("recurringId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("고정비 템플릿 ID (고정비에서 생성된 경우)"),
            fieldWithPath("isRecurringModified")
                .type(JsonFieldType.BOOLEAN)
                .description("고정비 인스턴스가 개별 수정되었는지 여부"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** 테스트용 지출 생성 → 생성된 id 반환 */
    private fun createExpense(token: String): String {
        val res =
            mockMvc
                .post("/expenses") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-05-10",
                                "itemName" to "장미 100송이",
                                "category" to "flower_purchase",
                                "unitPrice" to 5_000,
                                "quantity" to 3,
                                "paymentMethod" to "card",
                                "cardCompany" to "신한카드",
                                "vendor" to "양재 꽃시장",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 지출 생성 ───────────────────────────────────────────────────────────

    @Test
    fun `지출 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/expenses") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to "2026-05-10",
                            "itemName" to "장미 100송이",
                            "category" to "flower_purchase",
                            "unitPrice" to 5_000,
                            "quantity" to 3,
                            "paymentMethod" to "card",
                            "cardCompany" to "신한카드",
                            "vendor" to "양재 꽃시장",
                            "memo" to "웨딩 준비",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "expense-create",
                        requestSchema = "ExpenseCreateRequest",
                        responseSchema = "ExpenseResponse",
                        tag = "Expenses",
                        summary = "지출 생성 (총액은 단가 × 수량으로 서버 계산)",
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .description("지출 발생일 (yyyy-MM-dd, 필수)"),
                                fieldWithPath("itemName")
                                    .type(JsonFieldType.STRING)
                                    .description("물품명 (필수)"),
                                fieldWithPath("category")
                                    .type(JsonFieldType.STRING)
                                    .description("지출 카테고리 (필수)"),
                                fieldWithPath("unitPrice")
                                    .type(JsonFieldType.NUMBER)
                                    .description("단가(원, 0 이상, 필수)"),
                                fieldWithPath("quantity")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("수량 (기본값 1, 1 이상)"),
                                fieldWithPath("paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("결제방식. cash | card | transfer | naverpay | kakaopay (필수)"),
                                fieldWithPath("cardCompany")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드사 (card 결제 시 입력)"),
                                fieldWithPath("vendor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("거래처"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고"),
                            ),
                        responseFields = expenseResponseFields,
                    ),
                )
            }
    }

    // ── 2. 지출 목록 조회 ──────────────────────────────────────────────────────

    @Test
    fun `지출 목록 문서화`() {
        val token = signupAndToken()
        createExpense(token)

        mockMvc
            .get("/expenses") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("month", "2026-05")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "expense-list",
                        responseSchema = "ExpenseListResponse",
                        tag = "Expenses",
                        summary = "지출 목록 (월 필터 선택, 미입력 시 전체)",
                        responseFields =
                            listOf(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("지출 ID"),
                                fieldWithPath("[].date")
                                    .type(JsonFieldType.STRING)
                                    .description("지출 발생일 (yyyy-MM-dd)"),
                                fieldWithPath("[].itemName").type(JsonFieldType.STRING).description("물품명"),
                                fieldWithPath("[].category").type(JsonFieldType.STRING).description("지출 카테고리"),
                                fieldWithPath("[].unitPrice").type(JsonFieldType.NUMBER).description("단가(원)"),
                                fieldWithPath("[].quantity").type(JsonFieldType.NUMBER).description("수량"),
                                fieldWithPath("[].totalAmount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("[서버 계산 SSOT] 총액 = 단가 × 수량"),
                                fieldWithPath("[].paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("결제방식"),
                                fieldWithPath("[].cardCompany")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드사"),
                                fieldWithPath("[].vendor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("거래처"),
                                fieldWithPath("[].memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고"),
                                fieldWithPath("[].recurringId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("고정비 템플릿 ID"),
                                fieldWithPath("[].isRecurringModified")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("고정비 인스턴스 개별 수정 여부"),
                                fieldWithPath("[].createdAt")
                                    .type(JsonFieldType.STRING)
                                    .description("생성 시각 (ISO-8601)"),
                                fieldWithPath("[].updatedAt")
                                    .type(JsonFieldType.STRING)
                                    .description("최종 수정 시각 (ISO-8601)"),
                            ),
                    ),
                )
            }
    }

    // ── 3. 지출 단건 조회 ──────────────────────────────────────────────────────

    @Test
    fun `지출 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createExpense(token)

        mockMvc
            .get("/expenses/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/expenses/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "expense-get",
                        responseSchema = "ExpenseResponse",
                        tag = "Expenses",
                        summary = "지출 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("지출 ID")),
                        responseFields = expenseResponseFields,
                    ),
                )
            }
    }

    // ── 4. 지출 수정 ───────────────────────────────────────────────────────────

    @Test
    fun `지출 수정 문서화`() {
        val token = signupAndToken()
        val id = createExpense(token)

        mockMvc
            .patch("/expenses/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/expenses/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "unitPrice" to 10_000,
                            "quantity" to 2,
                            "memo" to "수정된 비고",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "expense-update",
                        requestSchema = "ExpenseUpdateRequest",
                        responseSchema = "ExpenseResponse",
                        tag = "Expenses",
                        summary = "지출 수정 (제공된 필드만 반영, 총액 재계산)",
                        pathParameters = listOf(parameterWithName("id").description("지출 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("지출 발생일 변경 (yyyy-MM-dd)"),
                                fieldWithPath("itemName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("물품명 변경"),
                                fieldWithPath("category")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카테고리 변경"),
                                fieldWithPath("unitPrice")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("단가 변경 (원, 0 이상)"),
                                fieldWithPath("quantity")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("수량 변경 (1 이상)"),
                                fieldWithPath("paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("결제방식 변경. cash | card | transfer | naverpay | kakaopay"),
                                fieldWithPath("cardCompany")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드사 변경"),
                                fieldWithPath("vendor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("거래처 변경"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고 변경"),
                            ),
                        responseFields = expenseResponseFields,
                    ),
                )
            }
    }

    // ── 5. 지출 자동완성 (suggestions) ────────────────────────────────────────

    @Test
    fun `지출 자동완성 문서화`() {
        val token = signupAndToken()
        createExpense(token)

        mockMvc
            .get("/expenses/suggestions") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "expense-suggestions",
                        responseSchema = "ExpenseSuggestionsResponse",
                        tag = "Expenses",
                        summary = "지출 자동완성 (물품명/거래처/비고 빈도순 반환)",
                        responseFields =
                            listOf(
                                fieldWithPath("itemNames")
                                    .type(JsonFieldType.ARRAY)
                                    .description("물품명 자동완성 목록 (빈도 내림차순)"),
                                fieldWithPath("vendors")
                                    .type(JsonFieldType.ARRAY)
                                    .description("거래처 자동완성 목록 (빈도 내림차순)"),
                                fieldWithPath("memos")
                                    .type(JsonFieldType.ARRAY)
                                    .description("비고 자동완성 목록 (빈도 내림차순)"),
                            ),
                    ),
                )
            }
    }

    // ── 6. 지출 삭제 ───────────────────────────────────────────────────────────

    @Test
    fun `지출 삭제 문서화`() {
        val token = signupAndToken()
        val id = createExpense(token)

        mockMvc
            .delete("/expenses/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/expenses/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "expense-delete",
                        tag = "Expenses",
                        summary = "지출 삭제",
                        pathParameters = listOf(parameterWithName("id").description("지출 ID")),
                    ),
                )
            }
    }
}
