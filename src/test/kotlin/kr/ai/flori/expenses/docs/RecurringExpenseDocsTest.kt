package kr.ai.flori.expenses.docs

import kr.ai.flori.common.docs.RestDocsSupport
import kr.ai.flori.expenses.service.RecurringExpenseGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.sql.Date
import java.time.LocalDate

/**
 * RecurringExpenses API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * 고정비 템플릿(weekly/monthly/yearly)과 인스턴스(this/all) 분기 흐름을 포함한다.
 */
class RecurringExpenseDocsTest : RestDocsSupport() {
    @Autowired
    private lateinit var generator: RecurringExpenseGenerator

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    /** RecurringExpenseResponse 공통 응답 필드 — 단건/목록/토글/수정에서 재사용 */
    private val recurringResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("고정비 ID"),
            fieldWithPath("itemName").type(JsonFieldType.STRING).description("물품명"),
            fieldWithPath("category").type(JsonFieldType.STRING).description("지출 카테고리"),
            fieldWithPath("unitPrice").type(JsonFieldType.NUMBER).description("단가(원)"),
            fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("수량"),
            fieldWithPath("paymentMethod")
                .type(JsonFieldType.STRING)
                .description("결제방식. cash | card | transfer | naverpay | kakaopay"),
            fieldWithPath("vendor")
                .type(JsonFieldType.STRING)
                .optional()
                .description("거래처 (null 가능)"),
            fieldWithPath("note")
                .type(JsonFieldType.STRING)
                .optional()
                .description("비고 (null 가능)"),
            fieldWithPath("frequency")
                .type(JsonFieldType.STRING)
                .description("반복 주기. weekly | monthly | yearly"),
            fieldWithPath("intervalCount")
                .type(JsonFieldType.NUMBER)
                .description("반복 간격 (매N주/월/년, 기본값 1)"),
            fieldWithPath("daysOfWeek")
                .type(JsonFieldType.ARRAY)
                .description("반복 요일 목록 (weekly 전용, 0=일~6=토)"),
            fieldWithPath("daysOfMonth")
                .type(JsonFieldType.ARRAY)
                .description("반복 날짜 목록 (monthly 전용, 1~31)"),
            fieldWithPath("yearlyDates")
                .type(JsonFieldType.ARRAY)
                .description("반복 연간 날짜 목록 (yearly 전용, [{m:월,d:일}])"),
            fieldWithPath("yearlyDates[].m")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("월 (1~12)"),
            fieldWithPath("yearlyDates[].d")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("일 (1~31)"),
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("반복 시작일 (yyyy-MM-dd)"),
            fieldWithPath("endDate")
                .type(JsonFieldType.STRING)
                .optional()
                .description("반복 종료일 (yyyy-MM-dd, null이면 무기한)"),
            fieldWithPath("isActive").type(JsonFieldType.BOOLEAN).description("활성 여부"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** 고정비 생성 공통 요청 필드 */
    private val recurringRequestFields =
        listOf(
            fieldWithPath("itemName").type(JsonFieldType.STRING).description("물품명 (필수)"),
            fieldWithPath("category").type(JsonFieldType.STRING).description("지출 카테고리 (필수)"),
            fieldWithPath("unitPrice").type(JsonFieldType.NUMBER).description("단가(원, 0 이상, 필수)"),
            fieldWithPath("quantity")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("수량 (기본값 1, 1 이상)"),
            fieldWithPath("paymentMethod")
                .type(JsonFieldType.STRING)
                .description("결제방식. cash | card | transfer | naverpay | kakaopay (필수)"),
            fieldWithPath("vendor")
                .type(JsonFieldType.STRING)
                .optional()
                .description("거래처"),
            fieldWithPath("note")
                .type(JsonFieldType.STRING)
                .optional()
                .description("비고"),
            fieldWithPath("frequency")
                .type(JsonFieldType.STRING)
                .description("반복 주기. weekly | monthly | yearly (필수)"),
            fieldWithPath("intervalCount")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("반복 간격 (기본값 1)"),
            fieldWithPath("daysOfWeek")
                .type(JsonFieldType.ARRAY)
                .optional()
                .description("반복 요일 (weekly 전용, 0=일~6=토)"),
            fieldWithPath("daysOfMonth")
                .type(JsonFieldType.ARRAY)
                .optional()
                .description("반복 날짜 (monthly 전용, 1~31)"),
            fieldWithPath("yearlyDates")
                .type(JsonFieldType.ARRAY)
                .optional()
                .description("연간 날짜 (yearly 전용, [{m:월,d:일}])"),
            fieldWithPath("yearlyDates[].m")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("월 (1~12)"),
            fieldWithPath("yearlyDates[].d")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("일 (1~31)"),
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("반복 시작일 (yyyy-MM-dd, 필수)"),
            fieldWithPath("endDate")
                .type(JsonFieldType.STRING)
                .optional()
                .description("반복 종료일 (yyyy-MM-dd, 미입력 시 무기한)"),
            fieldWithPath("isActive")
                .type(JsonFieldType.BOOLEAN)
                .optional()
                .description("활성 여부 (기본값 true)"),
        )

    /** 테스트용 월별 고정비 생성 → 생성된 id 반환 */
    private fun createRecurring(token: String): String {
        val res =
            mockMvc
                .post("/recurring-expenses") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "itemName" to "월세",
                                "category" to "rent",
                                "unitPrice" to 500_000,
                                "quantity" to 1,
                                "paymentMethod" to "transfer",
                                "frequency" to "monthly",
                                "daysOfMonth" to listOf(15),
                                "startDate" to "2026-01-01",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    /**
     * 고정비 템플릿에서 스케줄러를 통해 생성된 인스턴스 ID를 반환한다.
     * scope=all 수정/삭제는 recurringId가 있는 인스턴스가 필요하므로 generator를 직접 호출한다.
     */
    private fun generateInstance(
        recurringId: String,
        date: LocalDate,
    ): String {
        generator.generateForDate(date)
        return jdbcTemplate
            .queryForObject(
                "SELECT id FROM expenses WHERE recurring_id = ?::bigint AND date = ?",
                Long::class.java,
                recurringId,
                Date.valueOf(date),
            )!!
            .toString()
    }

    // ── 1. 고정비 생성 ──────────────────────────────────────────────────────────

    @Test
    fun `고정비 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/recurring-expenses") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "itemName" to "월세",
                            "category" to "rent",
                            "unitPrice" to 500_000,
                            "quantity" to 1,
                            "paymentMethod" to "transfer",
                            "frequency" to "monthly",
                            "daysOfMonth" to listOf(15),
                            "startDate" to "2026-01-01",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-expense-create",
                        requestSchema = "RecurringExpenseRequest",
                        responseSchema = "RecurringExpenseResponse",
                        tag = "RecurringExpenses",
                        summary = "고정비 생성 (weekly/monthly/yearly 반복 규칙)",
                        requestFields = recurringRequestFields,
                        responseFields = recurringResponseFields,
                    ),
                )
            }
    }

    // ── 2. 고정비 목록 조회 ────────────────────────────────────────────────────

    @Test
    fun `고정비 목록 문서화`() {
        val token = signupAndToken()
        createRecurring(token)

        mockMvc
            .get("/recurring-expenses") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-expense-list",
                        responseSchema = "RecurringExpenseListResponse",
                        tag = "RecurringExpenses",
                        summary = "고정비 목록",
                        responseFields =
                            listOf(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("고정비 ID"),
                                fieldWithPath("[].itemName").type(JsonFieldType.STRING).description("물품명"),
                                fieldWithPath("[].category")
                                    .type(JsonFieldType.STRING)
                                    .description("지출 카테고리"),
                                fieldWithPath("[].unitPrice").type(JsonFieldType.NUMBER).description("단가(원)"),
                                fieldWithPath("[].quantity").type(JsonFieldType.NUMBER).description("수량"),
                                fieldWithPath("[].paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("결제방식"),
                                fieldWithPath("[].vendor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("거래처"),
                                fieldWithPath("[].note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고"),
                                fieldWithPath("[].frequency")
                                    .type(JsonFieldType.STRING)
                                    .description("반복 주기. weekly | monthly | yearly"),
                                fieldWithPath("[].intervalCount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("반복 간격"),
                                fieldWithPath("[].daysOfWeek")
                                    .type(JsonFieldType.ARRAY)
                                    .description("반복 요일 목록"),
                                fieldWithPath("[].daysOfMonth")
                                    .type(JsonFieldType.ARRAY)
                                    .description("반복 날짜 목록"),
                                fieldWithPath("[].yearlyDates")
                                    .type(JsonFieldType.ARRAY)
                                    .description("연간 날짜 목록"),
                                fieldWithPath("[].startDate")
                                    .type(JsonFieldType.STRING)
                                    .description("반복 시작일"),
                                fieldWithPath("[].endDate")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("반복 종료일"),
                                fieldWithPath("[].isActive")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("활성 여부"),
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

    // ── 3. 고정비 단건 조회 ────────────────────────────────────────────────────

    @Test
    fun `고정비 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createRecurring(token)

        mockMvc
            .get("/recurring-expenses/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/recurring-expenses/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-expense-get",
                        responseSchema = "RecurringExpenseResponse",
                        tag = "RecurringExpenses",
                        summary = "고정비 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("고정비 ID")),
                        responseFields = recurringResponseFields,
                    ),
                )
            }
    }

    // ── 4. 고정비 수정 (전체 교체) ─────────────────────────────────────────────

    @Test
    fun `고정비 수정 문서화`() {
        val token = signupAndToken()
        val id = createRecurring(token)

        mockMvc
            .put("/recurring-expenses/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/recurring-expenses/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "itemName" to "월세 (변경)",
                            "category" to "rent",
                            "unitPrice" to 550_000,
                            "quantity" to 1,
                            "paymentMethod" to "transfer",
                            "frequency" to "monthly",
                            "daysOfMonth" to listOf(15),
                            "startDate" to "2026-01-01",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-expense-update",
                        requestSchema = "RecurringExpenseRequest",
                        responseSchema = "RecurringExpenseResponse",
                        tag = "RecurringExpenses",
                        summary = "고정비 수정 (전체 교체 — PUT)",
                        pathParameters = listOf(parameterWithName("id").description("고정비 ID")),
                        requestFields = recurringRequestFields,
                        responseFields = recurringResponseFields,
                    ),
                )
            }
    }

    // ── 5. 고정비 활성/비활성 토글 ─────────────────────────────────────────────

    @Test
    fun `고정비 토글 문서화`() {
        val token = signupAndToken()
        val id = createRecurring(token)

        mockMvc
            .post("/recurring-expenses/$id/toggle") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/recurring-expenses/{id}/toggle")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("isActive" to false))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-expense-toggle",
                        requestSchema = "ToggleActiveRequest",
                        responseSchema = "RecurringExpenseResponse",
                        tag = "RecurringExpenses",
                        summary = "고정비 활성/비활성 토글",
                        pathParameters = listOf(parameterWithName("id").description("고정비 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("isActive")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("활성 여부 (true=활성, false=비활성, 필수)"),
                            ),
                        responseFields = recurringResponseFields,
                    ),
                )
            }
    }

    // ── 6. 빠른 추가 ───────────────────────────────────────────────────────────

    @Test
    fun `고정비 빠른 추가 문서화`() {
        val token = signupAndToken()
        val id = createRecurring(token)

        mockMvc
            .post("/recurring-expenses/$id/quick-add") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/recurring-expenses/{id}/quick-add")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-expense-quick-add",
                        responseSchema = "ExpenseResponse",
                        tag = "RecurringExpenses",
                        summary = "빠른 추가 (오늘 날짜로 즉시 지출 생성, 템플릿과 분리)",
                        pathParameters = listOf(parameterWithName("id").description("고정비 ID")),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("생성된 지출 ID"),
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .description("지출 발생일 (오늘 날짜, yyyy-MM-dd)"),
                                fieldWithPath("itemName").type(JsonFieldType.STRING).description("물품명"),
                                fieldWithPath("category").type(JsonFieldType.STRING).description("카테고리"),
                                fieldWithPath("unitPrice").type(JsonFieldType.NUMBER).description("단가(원)"),
                                fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("수량"),
                                fieldWithPath("totalAmount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("[서버 계산 SSOT] 총액 = 단가 × 수량"),
                                fieldWithPath("paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("결제방식"),
                                fieldWithPath("cardCompany")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드사"),
                                fieldWithPath("vendor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("거래처"),
                                fieldWithPath("note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고"),
                                fieldWithPath("recurringId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("빠른 추가는 템플릿 분리 — null 반환"),
                                fieldWithPath("isRecurringModified")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("고정비 인스턴스 개별 수정 여부"),
                                fieldWithPath("createdAt")
                                    .type(JsonFieldType.STRING)
                                    .description("생성 시각 (ISO-8601)"),
                                fieldWithPath("updatedAt")
                                    .type(JsonFieldType.STRING)
                                    .description("최종 수정 시각 (ISO-8601)"),
                            ),
                    ),
                )
            }
    }

    // ── 7. 인스턴스 수정 (scope=this) ─────────────────────────────────────────

    @Test
    fun `고정비 인스턴스 이것만 수정 문서화`() {
        val token = signupAndToken()
        // 고정비 생성 후 스케줄러 없이 quickAdd 로 인스턴스 확보
        val recurringId = createRecurring(token)
        val expenseId =
            objectMapper
                .readTree(
                    mockMvc
                        .post("/recurring-expenses/$recurringId/quick-add") {
                            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        }.andReturn()
                        .response.contentAsString,
                ).get("id")
                .asText()

        mockMvc
            .patch("/recurring-expenses/instances/$expenseId") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/recurring-expenses/instances/{expenseId}",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("scope", "this")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "unitPrice" to 600_000,
                            "note" to "이번 달만 인상",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-instance-update-this",
                        requestSchema = "RecurringInstanceUpdateRequest",
                        tag = "RecurringExpenses",
                        summary = "고정비 인스턴스 수정 — 이것만 (scope=this, 해당 지출만 변경)",
                        pathParameters = listOf(parameterWithName("expenseId").description("지출 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("날짜 변경 (yyyy-MM-dd)"),
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
                                    .description("결제방식 변경"),
                                fieldWithPath("vendor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("거래처 변경"),
                                fieldWithPath("note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고 변경"),
                            ),
                    ),
                )
            }
    }

    // ── 8. 인스턴스 수정 (scope=all) ──────────────────────────────────────────

    @Test
    fun `고정비 인스턴스 이후 모두 수정 문서화`() {
        val token = signupAndToken()
        val recurringId = createRecurring(token)
        // scope=all은 recurringId가 연결된 인스턴스가 필요하므로 generator로 생성한다
        val instanceDate = LocalDate.of(2026, 6, 15)
        val expenseId = generateInstance(recurringId, instanceDate)

        mockMvc
            .patch("/recurring-expenses/instances/$expenseId") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/recurring-expenses/instances/{expenseId}",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("scope", "all")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "itemName" to "관리비",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-instance-update-all",
                        requestSchema = "RecurringInstanceUpdateRequest",
                        tag = "RecurringExpenses",
                        summary = "고정비 인스턴스 수정 — 이후 모두 (scope=all, 템플릿+인스턴스 동시 변경)",
                        pathParameters = listOf(parameterWithName("expenseId").description("지출 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("날짜 변경 (yyyy-MM-dd)"),
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
                                    .description("결제방식 변경"),
                                fieldWithPath("vendor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("거래처 변경"),
                                fieldWithPath("note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고 변경"),
                            ),
                    ),
                )
            }
    }

    // ── 9. 인스턴스 삭제 (scope=this) ─────────────────────────────────────────

    @Test
    fun `고정비 인스턴스 이것만 삭제 문서화`() {
        val token = signupAndToken()
        val recurringId = createRecurring(token)
        val expenseId =
            objectMapper
                .readTree(
                    mockMvc
                        .post("/recurring-expenses/$recurringId/quick-add") {
                            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        }.andReturn()
                        .response.contentAsString,
                ).get("id")
                .asText()

        mockMvc
            .delete("/recurring-expenses/instances/$expenseId") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/recurring-expenses/instances/{expenseId}",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("scope", "this")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-instance-delete-this",
                        requestSchema = "EmptyRequest",
                        tag = "RecurringExpenses",
                        summary = "고정비 인스턴스 삭제 — 이것만 (scope=this, skip 기록 후 지출 삭제)",
                        pathParameters = listOf(parameterWithName("expenseId").description("지출 ID")),
                    ),
                )
            }
    }

    // ── 10. 인스턴스 삭제 (scope=all) ─────────────────────────────────────────

    @Test
    fun `고정비 인스턴스 이후 모두 삭제 문서화`() {
        val token = signupAndToken()
        val recurringId = createRecurring(token)
        // scope=all은 recurringId가 연결된 인스턴스가 필요하므로 generator로 생성한다
        val instanceDate = LocalDate.of(2026, 7, 15)
        val expenseId = generateInstance(recurringId, instanceDate)

        mockMvc
            .delete("/recurring-expenses/instances/$expenseId") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/recurring-expenses/instances/{expenseId}",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("scope", "all")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-instance-delete-all",
                        requestSchema = "EmptyRequest",
                        tag = "RecurringExpenses",
                        summary = "고정비 인스턴스 삭제 — 이후 모두 (scope=all, 템플릿 종료일 단축)",
                        pathParameters = listOf(parameterWithName("expenseId").description("지출 ID")),
                    ),
                )
            }
    }

    // ── 11. 고정비 삭제 ────────────────────────────────────────────────────────

    @Test
    fun `고정비 삭제 문서화`() {
        val token = signupAndToken()
        val id = createRecurring(token)

        mockMvc
            .delete("/recurring-expenses/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/recurring-expenses/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "recurring-expense-delete",
                        tag = "RecurringExpenses",
                        summary = "고정비 삭제 (템플릿 전체 삭제)",
                        pathParameters = listOf(parameterWithName("id").description("고정비 ID")),
                    ),
                )
            }
    }
}
