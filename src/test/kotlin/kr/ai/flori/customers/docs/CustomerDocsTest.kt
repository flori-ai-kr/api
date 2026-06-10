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
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("고객 ID"),
            fieldWithPath("name").type(JsonFieldType.STRING).description("고객 이름"),
            fieldWithPath("phone").type(JsonFieldType.STRING).description("전화번호"),
            fieldWithPath("gradeId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("고객 등급 id (등급 미지정이면 null)"),
            fieldWithPath("grade")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객 등급명 (배지 표기용, gradeId로 해석. 미지정이면 null)"),
            fieldWithPath("gradeLocked")
                .type(JsonFieldType.BOOLEAN)
                .description("등급 수동 고정 여부 (true면 매출 변경 시 자동 재계산 제외)"),
            fieldWithPath("gender")
                .type(JsonFieldType.STRING)
                .optional()
                .description("성별 (미입력이면 null)"),
            fieldWithPath("memo")
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
            fieldWithPath("photoThumbnails")
                .type(JsonFieldType.ARRAY)
                .description("이 고객에 연결된 사진첩 대표 썸네일 목록 (최신순 최대 6장)"),
            fieldWithPath("photoThumbnails[].url")
                .type(JsonFieldType.STRING)
                .optional()
                .description("썸네일 이미지 URL"),
            fieldWithPath("photoThumbnails[].cardId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("사진첩 딥링크용 photo_card id"),
            fieldWithPath("photoCount")
                .type(JsonFieldType.NUMBER)
                .description("이 고객에 연결된 사진첩 카드 총 개수"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** CustomerSearchResult 공통 응답 필드 — search / check-phone에서 재사용 */
    private val customerSearchResultFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("고객 ID"),
            fieldWithPath("name").type(JsonFieldType.STRING).description("고객 이름"),
            fieldWithPath("phone").type(JsonFieldType.STRING).description("전화번호"),
            fieldWithPath("grade")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객 등급명 (미지정이면 null)"),
        )

    /** SaleResponse 응답 필드 (고객별 매출 조회 sales[] 항목) */
    private val salesPageResponseFields =
        listOf(
            fieldWithPath("sales").type(JsonFieldType.ARRAY).description("매출 목록"),
            fieldWithPath("sales[].id").type(JsonFieldType.NUMBER).description("매출 ID"),
            fieldWithPath("sales[].date").type(JsonFieldType.STRING).description("매출 발생일 (yyyy-MM-dd)"),
            fieldWithPath("sales[].categoryId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("상품 카테고리 ID (null 가능)"),
            fieldWithPath("sales[].categoryLabel")
                .type(JsonFieldType.STRING)
                .optional()
                .description("상품 카테고리 이름 (null 가능)"),
            fieldWithPath("sales[].amount").type(JsonFieldType.NUMBER).description("결제 금액(원)"),
            fieldWithPath("sales[].paymentMethodId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("결제수단 ID (미수면 null)"),
            fieldWithPath("sales[].paymentMethodLabel")
                .type(JsonFieldType.STRING)
                .optional()
                .description("결제수단 이름 (미수면 null)"),
            fieldWithPath("sales[].channelId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("매출 채널 ID (null 가능)"),
            fieldWithPath("sales[].channelLabel")
                .type(JsonFieldType.STRING)
                .optional()
                .description("매출 채널 이름 (null 가능)"),
            fieldWithPath("sales[].customerName")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객명"),
            fieldWithPath("sales[].customerPhone")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객 전화번호"),
            fieldWithPath("sales[].customerId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("연결된 고객 ID"),
            fieldWithPath("sales[].memo").type(JsonFieldType.STRING).optional().description("비고"),
            fieldWithPath("sales[].isUnpaid").type(JsonFieldType.BOOLEAN).description("미수 여부"),
            fieldWithPath("sales[].hasReview")
                .type(JsonFieldType.BOOLEAN)
                .description("리뷰 보유 여부"),
            fieldWithPath("sales[].photos")
                .type(JsonFieldType.ARRAY)
                .description("연결된 사진 URL 목록 (없으면 빈 배열)"),
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
                            "categoryId" to saleCategoryId(token),
                            "amount" to 50_000,
                            "paymentMethodId" to salePaymentId(token),
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
                        responseSchema = "CustomerListResponse",
                        tag = "Customers",
                        summary = "고객 목록 (구매 통계 포함, 총 구매액 내림차순)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("고객 목록"),
                                fieldWithPath("[].id")
                                    .type(JsonFieldType.NUMBER)
                                    .description("고객 ID"),
                                fieldWithPath("[].name")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 이름"),
                                fieldWithPath("[].phone")
                                    .type(JsonFieldType.STRING)
                                    .description("전화번호"),
                                fieldWithPath("[].gradeId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("고객 등급 id (미지정이면 null)"),
                                fieldWithPath("[].grade")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 등급명 (gradeId로 해석. 미지정이면 null)"),
                                fieldWithPath("[].gradeLocked")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("등급 수동 고정 여부"),
                                fieldWithPath("[].gender")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("성별 (미입력이면 null)"),
                                fieldWithPath("[].memo")
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
                                fieldWithPath("[].photoThumbnails")
                                    .type(JsonFieldType.ARRAY)
                                    .description("대표 썸네일 목록 (최신순 최대 6장)"),
                                fieldWithPath("[].photoThumbnails[].url")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("썸네일 이미지 URL"),
                                fieldWithPath("[].photoThumbnails[].cardId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("사진첩 딥링크용 photo_card id"),
                                fieldWithPath("[].photoCount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("연결된 사진첩 카드 총 개수"),
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
                        responseSchema = "CustomerSearchResultListResponse",
                        tag = "Customers",
                        summary = "고객 이름 검색 (부분 일치)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("검색 결과 목록"),
                                fieldWithPath("[].id")
                                    .type(JsonFieldType.NUMBER)
                                    .description("고객 ID"),
                                fieldWithPath("[].name")
                                    .type(JsonFieldType.STRING)
                                    .description("고객 이름"),
                                fieldWithPath("[].phone")
                                    .type(JsonFieldType.STRING)
                                    .description("전화번호"),
                                fieldWithPath("[].grade")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 등급명 (미지정이면 null)"),
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
                        responseSchema = "CustomerSearchResult",
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
                        responseSchema = "CustomerResponse",
                        tag = "Customers",
                        summary = "고객 단건 조회 (구매 통계 포함)",
                        pathParameters = listOf(parameterWithName("id").description("고객 ID")),
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
                        responseSchema = "SalesPageResponse",
                        tag = "Customers",
                        summary = "고객별 매출 조회 (페이지네이션)",
                        pathParameters = listOf(parameterWithName("id").description("고객 ID")),
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
                            "gender" to "female",
                            "memo" to "VIP 예비 고객",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-create",
                        requestSchema = "CustomerCreateRequest",
                        responseSchema = "CustomerResponse",
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
                                fieldWithPath("gender")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("성별. male | female"),
                                fieldWithPath("memo")
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
                        requestSchema = "FindOrCreateCustomerRequest",
                        responseSchema = "CustomerResponse",
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
                            "memo" to "수정된 메모",
                            "gender" to "male",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-update",
                        requestSchema = "CustomerUpdateRequest",
                        responseSchema = "CustomerResponse",
                        tag = "Customers",
                        summary = "고객 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("고객 ID")),
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
                                fieldWithPath("gender")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("성별 변경. male | female"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 변경"),
                            ),
                        responseFields = customerResponseFields,
                    ),
                )
            }
    }

    // ── 10. 고객 등급 수동 지정 (잠금) ─────────────────────────────────────────

    /** 이 테넌트의 등급 중 하나의 id를 반환 (기본 시드: 신규/단골/VIP/블랙리스트). */
    private fun anyGradeId(token: String): Long {
        val res =
            mockMvc
                .get("/customer-grades") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        return objectMapper
            .readTree(res)
            .last()
            .get("id")
            .asLong()
    }

    @Test
    fun `고객 등급 수동 지정 문서화`() {
        val token = signupAndToken()
        val id = createCustomer(token)
        val gradeId = anyGradeId(token)

        mockMvc
            .patch("/customers/$id/grade") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}/grade")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("gradeId" to gradeId))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-update-grade",
                        requestSchema = "CustomerGradeAssignRequest",
                        responseSchema = "CustomerResponse",
                        tag = "Customers",
                        summary = "고객 등급 수동 지정 (지정 시 등급 잠금 → 자동 재계산 제외)",
                        pathParameters = listOf(parameterWithName("id").description("고객 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("gradeId")
                                    .type(JsonFieldType.NUMBER)
                                    .description("지정할 등급 id (필수). /customer-grades 의 id"),
                            ),
                        responseFields = customerResponseFields,
                    ),
                )
            }
    }

    // ── 10-2. 고객 등급 자동 되돌리기 (잠금 해제 + 재계산) ──────────────────────

    @Test
    fun `고객 등급 자동 되돌리기 문서화`() {
        val token = signupAndToken()
        val id = createCustomer(token)

        mockMvc
            .patch("/customers/$id/grade/auto") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}/grade/auto")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-grade-auto",
                        responseSchema = "CustomerResponse",
                        tag = "Customers",
                        summary = "고객 등급 자동 되돌리기 (잠금 해제 후 구매횟수 기준 재계산)",
                        pathParameters = listOf(parameterWithName("id").description("고객 ID")),
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
                        pathParameters = listOf(parameterWithName("id").description("고객 ID")),
                    ),
                )
            }
    }
}
