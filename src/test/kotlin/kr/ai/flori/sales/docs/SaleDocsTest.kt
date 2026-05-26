package kr.ai.flori.sales.docs

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
 * Sales API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 */
class SaleDocsTest : RestDocsSupport() {
    /** SaleResponse 공통 응답 필드 — 단건 조회/생성/수정/미수 처리에서 재사용 */
    private val saleResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.STRING).description("매출 UUID"),
            fieldWithPath("date").type(JsonFieldType.STRING).description("매출 발생일 (yyyy-MM-dd)"),
            fieldWithPath("productName").type(JsonFieldType.STRING).description("상품명"),
            fieldWithPath("productCategory")
                .type(JsonFieldType.STRING)
                .optional()
                .description("상품 카테고리 (null 가능)"),
            fieldWithPath("amount").type(JsonFieldType.NUMBER).description("결제 금액(원)"),
            fieldWithPath("paymentMethod").type(JsonFieldType.STRING).description("결제방식"),
            fieldWithPath("reservationChannel").type(JsonFieldType.STRING).description("예약 채널"),
            fieldWithPath("customerName")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객명 (미입력이면 null)"),
            fieldWithPath("customerPhone")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객 전화번호 (미입력이면 null)"),
            fieldWithPath("customerId")
                .type(JsonFieldType.STRING)
                .optional()
                .description("연결된 고객 UUID (미연결이면 null)"),
            fieldWithPath("note").type(JsonFieldType.STRING).optional().description("비고"),
            fieldWithPath("isUnpaid")
                .type(JsonFieldType.BOOLEAN)
                .description("미수 여부 (paymentMethod=unpaid 이면 true)"),
            fieldWithPath("hasReview").type(JsonFieldType.BOOLEAN).description("리뷰 보유 여부"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** SalesPageResponse 목록용 응답 필드 — list 엔드포인트에서 사용 */
    private val salesPageResponseFields =
        listOf(
            fieldWithPath("sales").type(JsonFieldType.ARRAY).description("매출 목록"),
            fieldWithPath("sales[].id").type(JsonFieldType.STRING).description("매출 UUID"),
            fieldWithPath("sales[].date").type(JsonFieldType.STRING).description("매출 발생일 (yyyy-MM-dd)"),
            fieldWithPath("sales[].productName").type(JsonFieldType.STRING).description("상품명"),
            fieldWithPath("sales[].productCategory")
                .type(JsonFieldType.STRING)
                .optional()
                .description("상품 카테고리 (null 가능)"),
            fieldWithPath("sales[].amount").type(JsonFieldType.NUMBER).description("결제 금액(원)"),
            fieldWithPath("sales[].paymentMethod").type(JsonFieldType.STRING).description("결제방식"),
            fieldWithPath("sales[].reservationChannel")
                .type(JsonFieldType.STRING)
                .description("예약 채널"),
            fieldWithPath("sales[].customerName")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객명"),
            fieldWithPath("sales[].customerPhone")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객 전화번호"),
            fieldWithPath("sales[].customerId")
                .type(JsonFieldType.STRING)
                .optional()
                .description("연결된 고객 UUID"),
            fieldWithPath("sales[].note").type(JsonFieldType.STRING).optional().description("비고"),
            fieldWithPath("sales[].isUnpaid").type(JsonFieldType.BOOLEAN).description("미수 여부"),
            fieldWithPath("sales[].hasReview").type(JsonFieldType.BOOLEAN).description("리뷰 보유 여부"),
            fieldWithPath("sales[].createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("sales[].updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
            fieldWithPath("hasMore")
                .type(JsonFieldType.BOOLEAN)
                .description("다음 페이지 존재 여부 (무한스크롤용)"),
        )

    /** 테스트용 카드 매출 생성 → 생성된 id 반환 */
    private fun createSale(token: String): String {
        val res =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-05-22",
                                "productCategory" to "basic_bouquet",
                                "amount" to 100_000,
                                "paymentMethod" to "card",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 매출 생성 ───────────────────────────────────────────────────────────

    @Test
    fun `매출 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to "2026-05-22",
                            "productCategory" to "basic_bouquet",
                            "amount" to 100_000,
                            "paymentMethod" to "card",
                            "reservationChannel" to "kakaotalk",
                            "customerName" to "김하늘",
                            "note" to "웨딩 부케",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-create",
                        tag = "Sales",
                        summary = "매출 생성",
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .description("매출 발생일 (yyyy-MM-dd, 필수)"),
                                fieldWithPath("productCategory")
                                    .type(JsonFieldType.STRING)
                                    .description("상품 카테고리(매출설정 value, 필수)"),
                                fieldWithPath("amount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("결제 금액(원, 0 이상, 필수)"),
                                fieldWithPath("paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("결제방식. 'unpaid' 이면 미수로 생성 (필수)"),
                                fieldWithPath("reservationChannel")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 채널. phone | kakaotalk | naver_booking | road | other"),
                                fieldWithPath("customerName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객명 (비회원 입력 가능)"),
                                fieldWithPath("customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호"),
                                fieldWithPath("customerId")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("연결할 고객 UUID (본인 소유 검증)"),
                                fieldWithPath("note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고"),
                            ),
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }

    // ── 2. 매출 목록 조회 ──────────────────────────────────────────────────────

    @Test
    fun `매출 목록 문서화`() {
        val token = signupAndToken()
        createSale(token)

        mockMvc
            .get("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("month", "2026-05")
                param("offset", "0")
                param("limit", "20")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-list",
                        tag = "Sales",
                        summary = "매출 목록 (무한스크롤 + 월·카테고리·결제방식·채널 필터 + 검색)",
                        responseFields = salesPageResponseFields,
                    ),
                )
            }
    }

    // ── 3. 매출 단건 조회 ──────────────────────────────────────────────────────

    @Test
    fun `매출 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createSale(token)

        mockMvc
            .get("/sales/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/sales/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-get",
                        tag = "Sales",
                        summary = "매출 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("매출 UUID")),
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }

    // ── 4. 매출 수정 ───────────────────────────────────────────────────────────

    @Test
    fun `매출 수정 문서화`() {
        val token = signupAndToken()
        val id = createSale(token)

        mockMvc
            .patch("/sales/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/sales/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "amount" to 120_000,
                            "note" to "수정된 비고",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-update",
                        tag = "Sales",
                        summary = "매출 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("매출 UUID")),
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("매출 발생일 변경"),
                                fieldWithPath("productCategory")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상품 카테고리 변경"),
                                fieldWithPath("amount")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("결제 금액 변경(원, 0 이상)"),
                                fieldWithPath("paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("결제방식 변경"),
                                fieldWithPath("reservationChannel")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 채널 변경. phone | kakaotalk | naver_booking | road | other"),
                                fieldWithPath("customerName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객명 변경"),
                                fieldWithPath("customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호 변경"),
                                fieldWithPath("customerId")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("연결 고객 UUID 변경"),
                                fieldWithPath("note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고 변경"),
                                fieldWithPath("hasReview")
                                    .type(JsonFieldType.BOOLEAN)
                                    .optional()
                                    .description("리뷰 보유 여부 변경"),
                            ),
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }

    // ── 5. 미수 완료 (complete-unpaid) ────────────────────────────────────────

    @Test
    fun `미수 완료 문서화`() {
        val token = signupAndToken()

        // 미수 매출 생성
        val res =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-05-22",
                                "productCategory" to "basic_bouquet",
                                "amount" to 80_000,
                                "paymentMethod" to "unpaid",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val id = objectMapper.readTree(res).get("id").asText()

        mockMvc
            .post("/sales/$id/complete-unpaid") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/sales/{id}/complete-unpaid")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("paymentMethod" to "cash"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-complete-unpaid",
                        tag = "Sales",
                        summary = "미수 완료 (미수 매출의 결제방식 확정)",
                        pathParameters = listOf(parameterWithName("id").description("매출 UUID")),
                        requestFields =
                            listOf(
                                fieldWithPath("paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("확정할 결제방식 (unpaid 제외, 필수)"),
                            ),
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }

    // ── 6. 미수 되돌리기 (revert-unpaid) ─────────────────────────────────────

    @Test
    fun `미수 되돌리기 문서화`() {
        val token = signupAndToken()

        // 미수 → 완료 → 되돌리기 순서
        val res =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-05-22",
                                "productCategory" to "basic_bouquet",
                                "amount" to 60_000,
                                "paymentMethod" to "unpaid",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val id = objectMapper.readTree(res).get("id").asText()

        // 먼저 결제방식 확정
        mockMvc
            .post("/sales/$id/complete-unpaid") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("paymentMethod" to "cash"))
            }.andReturn()

        // 되돌리기 문서화
        mockMvc
            .post("/sales/$id/revert-unpaid") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/sales/{id}/revert-unpaid")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-revert-unpaid",
                        tag = "Sales",
                        summary = "미수 되돌리기 (결제방식을 다시 미수로)",
                        pathParameters = listOf(parameterWithName("id").description("매출 UUID")),
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }

    // ── 7. 비고 자동완성 (suggestions) ───────────────────────────────────────

    @Test
    fun `비고 자동완성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/sales/suggestions") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-suggestions",
                        tag = "Sales",
                        summary = "비고 자동완성 (과거 비고를 빈도순으로 반환)",
                        responseFields =
                            listOf(
                                fieldWithPath("notes")
                                    .type(JsonFieldType.ARRAY)
                                    .description("비고 자동완성 목록 (빈도 내림차순)"),
                            ),
                    ),
                )
            }
    }

    // ── 8. 매출 삭제 ───────────────────────────────────────────────────────────

    @Test
    fun `매출 삭제 문서화`() {
        val token = signupAndToken()
        val id = createSale(token)

        mockMvc
            .delete("/sales/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/sales/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "sale-delete",
                        tag = "Sales",
                        summary = "매출 삭제",
                        pathParameters = listOf(parameterWithName("id").description("매출 UUID")),
                    ),
                )
            }
    }
}
