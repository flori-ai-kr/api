package kr.ai.flori.deposits.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Deposits API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * depositStatus(pending/completed/not_applicable)는 서버가 계산하는 SSOT 값이다.
 */
class DepositDocsTest : RestDocsSupport() {
    /** SaleResponse 공통 응답 필드 — 입금 관련 엔드포인트에서 재사용 */
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
            fieldWithPath("cardCompany")
                .type(JsonFieldType.STRING)
                .optional()
                .description("카드사 (카드 결제일 때만 존재)"),
            fieldWithPath("fee")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("[서버 계산 SSOT] 카드 수수료 (amount × fee_rate/100). 카드 결제가 아니면 null"),
            fieldWithPath("expectedDeposit")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("[서버 계산 SSOT] 예상 입금액 (amount - fee). 카드 결제가 아니면 null"),
            fieldWithPath("expectedDepositDate")
                .type(JsonFieldType.STRING)
                .optional()
                .description("[서버 계산 SSOT] 입금 예정일 (영업일 N일 후). 카드 결제가 아니면 null"),
            fieldWithPath("depositStatus")
                .type(JsonFieldType.STRING)
                .description("[서버 계산 SSOT] 입금 상태. not_applicable | pending | completed"),
            fieldWithPath("depositedAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("실제 입금 확인 시각 (ISO-8601, 입금 전이면 null)"),
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

    /** 테스트용 카드 매출 생성 → 생성된 sale id 반환 */
    private fun createCardSale(token: String): String {
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
                                "cardCompany" to "신한카드",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 입금 목록 조회 ──────────────────────────────────────────────────────

    @Test
    fun `입금 목록 조회 문서화`() {
        val token = signupAndToken()
        createCardSale(token)

        mockMvc
            .get("/deposits") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("month", "2026-05")
                param("status", "pending")
                param("cardCompany", "신한카드")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "deposit-list",
                        tag = "Deposits",
                        summary = "입금 목록 (카드 매출, status·card_company·month 필터)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("입금 대상 매출 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.STRING).description("매출 UUID"),
                                fieldWithPath("[].date")
                                    .type(JsonFieldType.STRING)
                                    .description("매출 발생일 (yyyy-MM-dd)"),
                                fieldWithPath("[].productName")
                                    .type(JsonFieldType.STRING)
                                    .description("상품명"),
                                fieldWithPath("[].productCategory")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상품 카테고리 (null 가능)"),
                                fieldWithPath("[].amount").type(JsonFieldType.NUMBER).description("결제 금액(원)"),
                                fieldWithPath("[].paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("결제방식"),
                                fieldWithPath("[].cardCompany")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드사 (카드 결제일 때만 존재)"),
                                fieldWithPath("[].fee")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("[서버 계산 SSOT] 카드 수수료"),
                                fieldWithPath("[].expectedDeposit")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("[서버 계산 SSOT] 예상 입금액"),
                                fieldWithPath("[].expectedDepositDate")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("[서버 계산 SSOT] 입금 예정일"),
                                fieldWithPath("[].depositStatus")
                                    .type(JsonFieldType.STRING)
                                    .description("[서버 계산 SSOT] 입금 상태"),
                                fieldWithPath("[].depositedAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("실제 입금 확인 시각"),
                                fieldWithPath("[].reservationChannel")
                                    .type(JsonFieldType.STRING)
                                    .description("예약 채널"),
                                fieldWithPath("[].customerName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객명"),
                                fieldWithPath("[].customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호"),
                                fieldWithPath("[].customerId")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("연결된 고객 UUID"),
                                fieldWithPath("[].note")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고"),
                                fieldWithPath("[].isUnpaid")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("미수 여부"),
                                fieldWithPath("[].hasReview")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("리뷰 보유 여부"),
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

    // ── 2. 입금 요약 조회 ──────────────────────────────────────────────────────

    @Test
    fun `입금 요약 조회 문서화`() {
        val token = signupAndToken()
        createCardSale(token)

        mockMvc
            .get("/deposits/summary") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("month", "2026-05")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "deposit-summary",
                        tag = "Deposits",
                        summary = "입금 요약 (대기/완료 건수·금액)",
                        responseFields =
                            listOf(
                                fieldWithPath("pendingCount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("입금 대기 건수"),
                                fieldWithPath("pendingAmount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("입금 대기 총 예상 입금액(원)"),
                                fieldWithPath("completedCount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("입금 완료 건수"),
                                fieldWithPath("completedAmount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("입금 완료 총 예상 입금액(원)"),
                            ),
                    ),
                )
            }
    }

    // ── 3. 입금 단건 확인 ──────────────────────────────────────────────────────

    @Test
    fun `입금 단건 확인 문서화`() {
        val token = signupAndToken()
        val id = createCardSale(token)

        mockMvc
            .post("/deposits/$id/confirm") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "deposit-confirm",
                        tag = "Deposits",
                        summary = "입금 단건 확인 (depositStatus=completed, deposited_at 기록)",
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }

    // ── 4. 입금 다건 확인 ──────────────────────────────────────────────────────

    @Test
    fun `입금 다건 확인 문서화`() {
        val token = signupAndToken()
        val id1 = createCardSale(token)
        val id2 = createCardSale(token)

        mockMvc
            .post("/deposits/confirm") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("ids" to listOf(id1, id2)))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "deposit-confirm-multiple",
                        tag = "Deposits",
                        summary = "입금 다건 일괄 확인",
                        requestFields =
                            listOf(
                                fieldWithPath("ids")
                                    .type(JsonFieldType.ARRAY)
                                    .description("확인할 매출 UUID 목록 (필수, 1개 이상)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("confirmed")
                                    .type(JsonFieldType.NUMBER)
                                    .description("실제 처리된 건수 (본인 소유 카드 매출만 반영)"),
                            ),
                    ),
                )
            }
    }

    // ── 5. 입금 되돌리기 ───────────────────────────────────────────────────────

    @Test
    fun `입금 되돌리기 문서화`() {
        val token = signupAndToken()
        val id = createCardSale(token)

        // 먼저 입금 확인(confirmed) 후 되돌리기
        mockMvc
            .post("/deposits/$id/confirm") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andReturn()

        mockMvc
            .post("/deposits/$id/revert") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "deposit-revert",
                        tag = "Deposits",
                        summary = "입금 되돌리기 (depositStatus=pending, deposited_at 제거)",
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }
}
