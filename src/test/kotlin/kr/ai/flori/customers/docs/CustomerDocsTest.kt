package kr.ai.flori.customers.docs

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
 * Customers API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * totalPurchaseCount / totalPurchaseAmount / firstPurchaseDate / lastPurchaseDate 는
 * 서버가 매출에서 실시간 집계하는 SSOT 값이다.
 */
class CustomerDocsTest : RestDocsSupport() {
    /** CustomerResponse 공통 응답 필드 — 단건 조회/생성/수정/등급변경에서 재사용 */
    private val customerResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.STRING).description("고객 UUID"),
            fieldWithPath("name").type(JsonFieldType.STRING).description("고객 이름"),
            fieldWithPath("phone").type(JsonFieldType.STRING).description("전화번호"),
            fieldWithPath("grade")
                .type(JsonFieldType.STRING)
                .description("고객 등급. new | regular | vip | vvip"),
            fieldWithPath("gender")
                .type(JsonFieldType.STRING)
                .optional()
                .description("성별 (미입력이면 null)"),
            fieldWithPath("note")
                .type(JsonFieldType.STRING)
                .optional()
                .description("메모 (미입력이면 null)"),
            fieldWithPath("totalPurchaseCount")
                .type(JsonFieldType.NUMBER)
                .description("[서버 계산 SSOT] 총 구매 건수"),
            fieldWithPath("totalPurchaseAmount")
                .type(JsonFieldType.NUMBER)
                .description("[서버 계산 SSOT] 총 구매 금액(원)"),
            fieldWithPath("firstPurchaseDate")
                .type(JsonFieldType.STRING)
                .optional()
                .description("[서버 계산 SSOT] 첫 구매일 (yyyy-MM-dd, 구매 없으면 null)"),
            fieldWithPath("lastPurchaseDate")
                .type(JsonFieldType.STRING)
                .optional()
                .description("[서버 계산 SSOT] 최근 구매일 (yyyy-MM-dd, 구매 없으면 null)"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** CustomerSearchResult 공통 응답 필드 — search / check-phone에서 재사용 */
    private val customerSearchResultFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.STRING).description("고객 UUID"),
            fieldWithPath("name").type(JsonFieldType.STRING).description("고객 이름"),
            fieldWithPath("phone").type(JsonFieldType.STRING).description("전화번호"),
            fieldWithPath("grade")
                .type(JsonFieldType.STRING)
                .description("고객 등급. new | regular | vip | vvip"),
        )

    /** SaleResponse 응답 필드 (고객별 매출 조회 sales[] 항목) */
    private val salesPageResponseFields =
        listOf(
            fieldWithPath("sales").type(JsonFieldType.ARRAY).description("매출 목록"),
            fieldWithPath("sales[].id").type(JsonFieldType.STRING).description("매출 UUID"),
            fieldWithPath("sales[].date").type(JsonFieldType.STRING).description("매출 발생일 (yyyy-MM-dd)"),
            fieldWithPath("sales[].productName")
                .type(JsonFieldType.STRING)
                .description("상품명"),
            fieldWithPath("sales[].productCategory")
                .type(JsonFieldType.STRING)
                .optional()
                .description("상품 카테고리 (null 가능)"),
            fieldWithPath("sales[].amount").type(JsonFieldType.NUMBER).description("결제 금액(원)"),
            fieldWithPath("sales[].paymentMethod")
                .type(JsonFieldType.STRING)
                .description("결제방식"),
            fieldWithPath("sales[].cardCompany")
                .type(JsonFieldType.STRING)
                .optional()
                .description("카드사 (카드 결제일 때만 존재)"),
            fieldWithPath("sales[].fee")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("[서버 계산 SSOT] 카드 수수료"),
            fieldWithPath("sales[].expectedDeposit")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("[서버 계산 SSOT] 예상 입금액"),
            fieldWithPath("sales[].expectedDepositDate")
                .type(JsonFieldType.STRING)
                .optional()
                .description("[서버 계산 SSOT] 입금 예정일"),
            fieldWithPath("sales[].depositStatus")
                .type(JsonFieldType.STRING)
                .description("[서버 계산 SSOT] 입금 상태"),
            fieldWithPath("sales[].depositedAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("실제 입금 확인 시각"),
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
            fieldWithPath("sales[].hasReview")
                .type(JsonFieldType.BOOLEAN)
                .description("리뷰 보유 여부"),
            fieldWithPath("sales[].createdAt")
                .type(JsonFieldType.STRING)
                .description("생성 시각 (ISO-8601)"),
            fieldWithPath("sales[].updatedAt")
                .type(JsonFieldType.STRING)
                .description("최종 수정 시각 (ISO-8601)"),
            fieldWithPath("hasMore")
                .type(JsonFieldType.BOOLEAN)
                .description("다음 페이지 존재 여부 (무한스크롤용)"),
        )

    /** 테스트용 고객 생성 → 생성된 id 반환 */
    private fun createCustomer(token: String): String {
        val res =
            mockMvc
                .post("/customers") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("name" to "홍길동", "phone" to "010-${(10000000..99999999).random()}"))
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    /** 고객에게 매출을 하나 연결 (통계 non-trivial 용) */
    private fun createSaleForCustomer(
        token: String,
        customerId: String,
    ) {
        mockMvc
            .post("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to "2026-05-22",
                            "productCategory" to "basic_bouquet",
                            "amount" to 50_000,
                            "paymentMethod" to "cash",
                            "customerId" to customerId,
                        ),
                    )
            }.andReturn()
    }

    // ── 1. 고객 목록 조회 ──────────────────────────────────────────────────────

    @Test
    fun `고객 목록 조회 문서화`() {
        val token = signupAndToken()
        val customerId = createCustomer(token)
        createSaleForCustomer(token, customerId)

        mockMvc
            .get("/customers") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-list",
                        tag = "Customers",
                        summary = "고객 목록 (구매 통계 포함, 총 구매액 내림차순)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("고객 목록"),
                                fieldWithPath("[].id")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 UUID"),
                                fieldWithPath("[].name")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 이름"),
                                fieldWithPath("[].phone")
                                    .type(JsonFieldType.STRING)
                                    .description("전화번호"),
                                fieldWithPath("[].grade")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 등급. new | regular | vip | vvip"),
                                fieldWithPath("[].gender")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("성별 (미입력이면 null)"),
                                fieldWithPath("[].note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 (미입력이면 null)"),
                                fieldWithPath("[].totalPurchaseCount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("[서버 계산 SSOT] 총 구매 건수"),
                                fieldWithPath("[].totalPurchaseAmount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("[서버 계산 SSOT] 총 구매 금액(원)"),
                                fieldWithPath("[].firstPurchaseDate")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("[서버 계산 SSOT] 첫 구매일 (yyyy-MM-dd)"),
                                fieldWithPath("[].lastPurchaseDate")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("[서버 계산 SSOT] 최근 구매일 (yyyy-MM-dd)"),
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

    // ── 2. 고객 이름 검색 ──────────────────────────────────────────────────────

    @Test
    fun `고객 이름 검색 문서화`() {
        val token = signupAndToken()
        createCustomer(token)

        mockMvc
            .get("/customers/search") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("q", "홍")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-search",
                        tag = "Customers",
                        summary = "고객 이름 검색 (부분 일치)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("검색 결과 목록"),
                                fieldWithPath("[].id")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 UUID"),
                                fieldWithPath("[].name")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 이름"),
                                fieldWithPath("[].phone")
                                    .type(JsonFieldType.STRING)
                                    .description("전화번호"),
                                fieldWithPath("[].grade")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 등급. new | regular | vip | vvip"),
                            ),
                    ),
                )
            }
    }

    // ── 3. 전화번호 중복 확인 (중복 있음 → 200) ────────────────────────────────

    @Test
    fun `전화번호 중복 확인 문서화 - 중복 있음`() {
        val token = signupAndToken()
        val phone = "010-1111-2222"

        // 동일 전화번호 고객 미리 생성
        mockMvc
            .post("/customers") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "중복테스트", "phone" to phone))
            }.andReturn()

        mockMvc
            .get("/customers/check-phone") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("phone", phone)
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-check-phone-found",
                        tag = "Customers",
                        summary = "전화번호 중복 확인 — 중복 있음 (200 + 해당 고객 반환)",
                        responseFields = customerSearchResultFields,
                    ),
                )
            }
    }

    // ── 4. 전화번호 중복 확인 (중복 없음 → 204) ────────────────────────────────

    @Test
    fun `전화번호 중복 확인 문서화 - 중복 없음`() {
        val token = signupAndToken()

        mockMvc
            .get("/customers/check-phone") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("phone", "010-9999-0000")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-check-phone-not-found",
                        tag = "Customers",
                        summary = "전화번호 중복 확인 — 중복 없음 (204 No Content)",
                    ),
                )
            }
    }

    // ── 5. 고객 단건 조회 ──────────────────────────────────────────────────────

    @Test
    fun `고객 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createCustomer(token)
        createSaleForCustomer(token, id)

        mockMvc
            .get("/customers/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-get",
                        tag = "Customers",
                        summary = "고객 단건 조회 (구매 통계 포함)",
                        pathParameters = listOf(parameterWithName("id").description("고객 UUID")),
                        responseFields = customerResponseFields,
                    ),
                )
            }
    }

    // ── 6. 고객별 매출 조회 ────────────────────────────────────────────────────

    @Test
    fun `고객별 매출 조회 문서화`() {
        val token = signupAndToken()
        val id = createCustomer(token)
        createSaleForCustomer(token, id)

        mockMvc
            .get("/customers/$id/sales") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}/sales")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("page", "0")
                param("size", "10")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-sales",
                        tag = "Customers",
                        summary = "고객별 매출 조회 (페이지네이션)",
                        pathParameters = listOf(parameterWithName("id").description("고객 UUID")),
                        responseFields = salesPageResponseFields,
                    ),
                )
            }
    }

    // ── 7. 고객 생성 ───────────────────────────────────────────────────────────

    @Test
    fun `고객 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/customers") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "김하늘",
                            "phone" to "010-5555-6666",
                            "grade" to "regular",
                            "gender" to "female",
                            "note" to "VIP 예비 고객",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-create",
                        tag = "Customers",
                        summary = "고객 생성",
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 이름 (필수)"),
                                fieldWithPath("phone")
                                    .type(JsonFieldType.STRING)
                                    .description("전화번호 (필수, 계정 내 유일)"),
                                fieldWithPath("grade")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 등급 (기본값: new). new | regular | vip | vvip"),
                                fieldWithPath("gender")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("성별. male | female"),
                                fieldWithPath("note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모"),
                            ),
                        responseFields = customerResponseFields,
                    ),
                )
            }
    }

    // ── 8. 고객 찾기/생성 (find-or-create) ────────────────────────────────────

    @Test
    fun `고객 찾기 또는 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/customers/find-or-create") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "이민준",
                            "phone" to "010-7777-8888",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-find-or-create",
                        tag = "Customers",
                        summary = "고객 찾기/생성 (전화번호+계정 복합키 기준 — 기존이면 반환, 신규이면 생성)",
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 이름 (필수)"),
                                fieldWithPath("phone")
                                    .type(JsonFieldType.STRING)
                                    .description("전화번호 (필수)"),
                            ),
                        responseFields = customerResponseFields,
                    ),
                )
            }
    }

    // ── 9. 고객 수정 ───────────────────────────────────────────────────────────

    @Test
    fun `고객 수정 문서화`() {
        val token = signupAndToken()
        val id = createCustomer(token)

        mockMvc
            .patch("/customers/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "note" to "수정된 메모",
                            "gender" to "male",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-update",
                        tag = "Customers",
                        summary = "고객 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("고객 UUID")),
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 이름 변경"),
                                fieldWithPath("phone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("전화번호 변경"),
                                fieldWithPath("grade")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("등급 변경. new | regular | vip | vvip"),
                                fieldWithPath("gender")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("성별 변경. male | female"),
                                fieldWithPath("note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 변경"),
                            ),
                        responseFields = customerResponseFields,
                    ),
                )
            }
    }

    // ── 10. 고객 등급 변경 ─────────────────────────────────────────────────────

    @Test
    fun `고객 등급 변경 문서화`() {
        val token = signupAndToken()
        val id = createCustomer(token)

        mockMvc
            .patch("/customers/$id/grade") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}/grade")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("grade" to "vip"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-update-grade",
                        tag = "Customers",
                        summary = "고객 등급 변경",
                        pathParameters = listOf(parameterWithName("id").description("고객 UUID")),
                        requestFields =
                            listOf(
                                fieldWithPath("grade")
                                    .type(JsonFieldType.STRING)
                                    .description("변경할 등급 (필수). new | regular | vip | vvip"),
                            ),
                        responseFields = customerResponseFields,
                    ),
                )
            }
    }

    // ── 11. 고객 삭제 ──────────────────────────────────────────────────────────

    @Test
    fun `고객 삭제 문서화`() {
        val token = signupAndToken()
        val id = createCustomer(token)

        mockMvc
            .delete("/customers/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-delete",
                        tag = "Customers",
                        summary = "고객 삭제",
                        pathParameters = listOf(parameterWithName("id").description("고객 UUID")),
                    ),
                )
            }
    }
}
