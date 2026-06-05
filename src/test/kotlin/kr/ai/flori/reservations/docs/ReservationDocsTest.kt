package kr.ai.flori.reservations.docs

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
 * Reservations API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * status 허용값: pending | confirmed | completed | cancelled (DB CHECK 제약)
 */
class ReservationDocsTest : RestDocsSupport() {
    /** ReservationResponse 공통 응답 필드 — 단건/목록/액션 응답에서 재사용 */
    private val reservationResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("예약 ID"),
            fieldWithPath("date").type(JsonFieldType.STRING).description("예약일 (yyyy-MM-dd)"),
            fieldWithPath("time")
                .type(JsonFieldType.STRING)
                .optional()
                .description("예약 시각 (HH:mm:ss, 미입력이면 null)"),
            fieldWithPath("customerName").type(JsonFieldType.STRING).description("고객명"),
            fieldWithPath("customerPhone")
                .type(JsonFieldType.STRING)
                .optional()
                .description("고객 전화번호 (미입력이면 null)"),
            fieldWithPath("title").type(JsonFieldType.STRING).description("예약 제목"),
            fieldWithPath("memo")
                .type(JsonFieldType.STRING)
                .optional()
                .description("상세 메모 (미입력이면 null)"),
            fieldWithPath("status")
                .type(JsonFieldType.STRING)
                .description("예약 상태. pending | confirmed | completed | cancelled"),
            fieldWithPath("saleId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("연결된 매출 ID (매출 전환 전이면 null)"),
            fieldWithPath("amount").type(JsonFieldType.NUMBER).description("예약 금액(원)"),
            fieldWithPath("reminderAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("리마인더 발송 예정 시각 (ISO-8601, 미설정이면 null)"),
            fieldWithPath("reminderSent")
                .type(JsonFieldType.BOOLEAN)
                .description("리마인더 발송 여부 (reminderAt 변경 시 false로 리셋)"),
            fieldWithPath("pickupCompleted").type(JsonFieldType.BOOLEAN).description("픽업 완료 여부"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** SaleResponse 공통 응답 필드 — convertToSale 응답에서 사용 */
    private val saleResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("매출 ID"),
            fieldWithPath("date").type(JsonFieldType.STRING).description("매출 발생일 (yyyy-MM-dd)"),
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
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("연결된 고객 ID (미연결이면 null)"),
            fieldWithPath("memo").type(JsonFieldType.STRING).optional().description("비고"),
            fieldWithPath("isUnpaid").type(JsonFieldType.BOOLEAN).description("미수 여부"),
            fieldWithPath("hasReview").type(JsonFieldType.BOOLEAN).description("리뷰 보유 여부"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** 테스트용 예약 생성 → 생성된 id 반환 */
    private fun createReservation(token: String): String {
        val res =
            mockMvc
                .post("/reservations") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-06-01",
                                "customerName" to "홍길동",
                                "title" to "생일 꽃다발",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 예약 생성 ───────────────────────────────────────────────────────────

    @Test
    fun `예약 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/reservations") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to "2026-06-01",
                            "time" to "14:00:00",
                            "customerName" to "김하늘",
                            "customerPhone" to "010-1234-5678",
                            "title" to "웨딩 부케",
                            "memo" to "흰 장미 위주",
                            "amount" to 150_000,
                            "status" to "confirmed",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-create",
                        requestSchema = "ReservationCreateRequest",
                        responseSchema = "ReservationResponse",
                        tag = "Reservations",
                        summary = "예약 생성",
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .description("예약일 (yyyy-MM-dd, 필수)"),
                                fieldWithPath("time")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 시각 (HH:mm:ss)"),
                                fieldWithPath("customerName")
                                    .type(JsonFieldType.STRING)
                                    .description("고객명 (필수)"),
                                fieldWithPath("customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호"),
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .description("예약 제목 (필수)"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상세 메모"),
                                fieldWithPath("amount")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("예약 금액(원, 기본값 0)"),
                                fieldWithPath("status")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description(
                                        "예약 상태 (기본값 pending). pending | confirmed | completed | cancelled",
                                    ),
                                fieldWithPath("reminderAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("리마인더 발송 예정 시각 (ISO-8601)"),
                            ),
                        responseFields = reservationResponseFields,
                    ),
                )
            }
    }

    // ── 2. 월별 예약 목록 ──────────────────────────────────────────────────────

    @Test
    fun `월별 예약 목록 문서화`() {
        val token = signupAndToken()
        createReservation(token)

        mockMvc
            .get("/reservations") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("month", "2026-06")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-list",
                        responseSchema = "ReservationListResponse",
                        tag = "Reservations",
                        summary = "월별 예약 목록",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("예약 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("예약 ID"),
                                fieldWithPath("[].date").type(JsonFieldType.STRING).description("예약일 (yyyy-MM-dd)"),
                                fieldWithPath("[].time")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 시각 (HH:mm:ss, 미입력이면 null)"),
                                fieldWithPath("[].customerName").type(JsonFieldType.STRING).description("고객명"),
                                fieldWithPath("[].customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).description("예약 제목"),
                                fieldWithPath("[].memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상세 메모"),
                                fieldWithPath("[].status")
                                    .type(JsonFieldType.STRING)
                                    .description("예약 상태. pending | confirmed | completed | cancelled"),
                                fieldWithPath("[].saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결된 매출 ID"),
                                fieldWithPath("[].amount").type(JsonFieldType.NUMBER).description("예약 금액(원)"),
                                fieldWithPath("[].reminderAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("리마인더 발송 예정 시각 (ISO-8601)"),
                                fieldWithPath("[].reminderSent")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("리마인더 발송 여부"),
                                fieldWithPath("[].pickupCompleted")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("픽업 완료 여부"),
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

    // ── 3. 다가오는 예약 ───────────────────────────────────────────────────────

    @Test
    fun `다가오는 예약 문서화`() {
        val token = signupAndToken()
        // 오늘 이후 날짜 예약을 생성해야 upcoming 목록에 노출된다
        mockMvc
            .post("/reservations") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to "2099-12-31",
                            "customerName" to "미래고객",
                            "title" to "웨딩 부케",
                        ),
                    )
            }.andReturn()

        mockMvc
            .get("/reservations/upcoming") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-upcoming",
                        responseSchema = "ReservationListResponse",
                        tag = "Reservations",
                        summary = "다가오는 예약 목록",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("다가오는 예약 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("예약 ID"),
                                fieldWithPath("[].date").type(JsonFieldType.STRING).description("예약일 (yyyy-MM-dd)"),
                                fieldWithPath("[].time")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 시각 (HH:mm:ss, 미입력이면 null)"),
                                fieldWithPath("[].customerName").type(JsonFieldType.STRING).description("고객명"),
                                fieldWithPath("[].customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).description("예약 제목"),
                                fieldWithPath("[].memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상세 메모"),
                                fieldWithPath("[].status")
                                    .type(JsonFieldType.STRING)
                                    .description("예약 상태. pending | confirmed | completed | cancelled"),
                                fieldWithPath("[].saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결된 매출 ID"),
                                fieldWithPath("[].amount").type(JsonFieldType.NUMBER).description("예약 금액(원)"),
                                fieldWithPath("[].reminderAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("리마인더 발송 예정 시각 (ISO-8601)"),
                                fieldWithPath("[].reminderSent")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("리마인더 발송 여부"),
                                fieldWithPath("[].pickupCompleted")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("픽업 완료 여부"),
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

    // ── 4. 발동된 리마인더 ─────────────────────────────────────────────────────

    @Test
    fun `발동된 리마인더 문서화`() {
        val token = signupAndToken()
        // 과거 시각(1시간 전)으로 reminderAt을 설정하면 48시간 윈도 안에 포함된다
        val rId = createReservation(token)
        val pastReminder =
            java.time.Instant
                .now()
                .minusSeconds(3_600)
                .toString()
        mockMvc
            .patch("/reservations/$rId") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("reminderAt" to pastReminder))
            }.andReturn()

        mockMvc
            .get("/reservations/reminders") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-reminders",
                        responseSchema = "ReservationListResponse",
                        tag = "Reservations",
                        summary = "발동된 리마인더 목록 (최근 48시간 내 도달한 리마인더)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("리마인더 발동 예약 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("예약 ID"),
                                fieldWithPath("[].date").type(JsonFieldType.STRING).description("예약일 (yyyy-MM-dd)"),
                                fieldWithPath("[].time")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 시각 (HH:mm:ss, 미입력이면 null)"),
                                fieldWithPath("[].customerName").type(JsonFieldType.STRING).description("고객명"),
                                fieldWithPath("[].customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).description("예약 제목"),
                                fieldWithPath("[].memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상세 메모"),
                                fieldWithPath("[].status")
                                    .type(JsonFieldType.STRING)
                                    .description("예약 상태. pending | confirmed | completed | cancelled"),
                                fieldWithPath("[].saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결된 매출 ID"),
                                fieldWithPath("[].amount").type(JsonFieldType.NUMBER).description("예약 금액(원)"),
                                fieldWithPath("[].reminderAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("리마인더 발송 예정 시각 (ISO-8601)"),
                                fieldWithPath("[].reminderSent")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("리마인더 발송 여부"),
                                fieldWithPath("[].pickupCompleted")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("픽업 완료 여부"),
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

    // ── 5. 예약 제목/메모 자동완성 ─────────────────────────────────────────────

    @Test
    fun `자동완성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/reservations/suggestions") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-suggestions",
                        responseSchema = "ReservationSuggestionsResponse",
                        tag = "Reservations",
                        summary = "예약 제목/메모 자동완성 (과거 사용 빈도순)",
                        responseFields =
                            listOf(
                                fieldWithPath("titles")
                                    .type(JsonFieldType.ARRAY)
                                    .description("제목 자동완성 목록 (빈도 내림차순)"),
                                fieldWithPath("memos")
                                    .type(JsonFieldType.ARRAY)
                                    .description("메모 자동완성 목록 (빈도 내림차순)"),
                            ),
                    ),
                )
            }
    }

    // ── 6. 매출 연결 예약 목록 ─────────────────────────────────────────────────

    @Test
    fun `매출 연결 예약 목록 문서화`() {
        val token = signupAndToken()

        // 매출 생성 → 예약 연결
        val saleRes =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-06-01",
                                "productCategory" to "basic_bouquet",
                                "amount" to 80_000,
                                "paymentMethod" to "cash",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val saleId = objectMapper.readTree(saleRes).get("id").asText()

        // 예약 생성 후 saleId 연결
        val rId = createReservation(token)
        mockMvc
            .patch("/reservations/$rId") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("saleId" to saleId))
            }.andReturn()

        mockMvc
            .get("/reservations/by-sale/$saleId") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/reservations/by-sale/{saleId}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-by-sale",
                        responseSchema = "ReservationListResponse",
                        tag = "Reservations",
                        summary = "매출 연결 예약 목록",
                        pathParameters = listOf(parameterWithName("saleId").description("매출 ID")),
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("매출에 연결된 예약 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("예약 ID"),
                                fieldWithPath("[].date").type(JsonFieldType.STRING).description("예약일 (yyyy-MM-dd)"),
                                fieldWithPath("[].time")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 시각 (HH:mm:ss, 미입력이면 null)"),
                                fieldWithPath("[].customerName").type(JsonFieldType.STRING).description("고객명"),
                                fieldWithPath("[].customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).description("예약 제목"),
                                fieldWithPath("[].memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상세 메모"),
                                fieldWithPath("[].status")
                                    .type(JsonFieldType.STRING)
                                    .description("예약 상태. pending | confirmed | completed | cancelled"),
                                fieldWithPath("[].saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결된 매출 ID"),
                                fieldWithPath("[].amount").type(JsonFieldType.NUMBER).description("예약 금액(원)"),
                                fieldWithPath("[].reminderAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("리마인더 발송 예정 시각 (ISO-8601)"),
                                fieldWithPath("[].reminderSent")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("리마인더 발송 여부"),
                                fieldWithPath("[].pickupCompleted")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("픽업 완료 여부"),
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

    // ── 7. 예약 단건 조회 ──────────────────────────────────────────────────────

    @Test
    fun `예약 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createReservation(token)

        mockMvc
            .get("/reservations/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/reservations/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-get",
                        responseSchema = "ReservationResponse",
                        tag = "Reservations",
                        summary = "예약 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("예약 ID")),
                        responseFields = reservationResponseFields,
                    ),
                )
            }
    }

    // ── 8. 예약 수정 ───────────────────────────────────────────────────────────

    @Test
    fun `예약 수정 문서화`() {
        val token = signupAndToken()
        val id = createReservation(token)

        mockMvc
            .patch("/reservations/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/reservations/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "status" to "confirmed",
                            "memo" to "수정된 메모",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-update",
                        requestSchema = "ReservationUpdateRequest",
                        responseSchema = "ReservationResponse",
                        tag = "Reservations",
                        summary = "예약 수정 (제공된 필드만 반영, reminderAt 변경 시 reminderSent 리셋)",
                        pathParameters = listOf(parameterWithName("id").description("예약 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약일 변경 (yyyy-MM-dd)"),
                                fieldWithPath("time")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("예약 시각 변경 (HH:mm:ss)"),
                                fieldWithPath("customerName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객명 변경"),
                                fieldWithPath("customerPhone")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("고객 전화번호 변경"),
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("제목 변경"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 변경"),
                                fieldWithPath("amount")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("금액 변경(원)"),
                                fieldWithPath("status")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("상태 변경. pending | confirmed | completed | cancelled"),
                                fieldWithPath("saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결 매출 ID 변경"),
                                fieldWithPath("reminderAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("리마인더 발송 예정 시각 변경 (ISO-8601, 변경 시 reminderSent 리셋)"),
                                fieldWithPath("pickupCompleted")
                                    .type(JsonFieldType.BOOLEAN)
                                    .optional()
                                    .description("픽업 완료 여부 변경"),
                            ),
                        responseFields = reservationResponseFields,
                    ),
                )
            }
    }

    // ── 9. 픽업 완료 처리 ──────────────────────────────────────────────────────

    @Test
    fun `픽업 완료 처리 문서화`() {
        val token = signupAndToken()
        val id = createReservation(token)

        mockMvc
            .post("/reservations/$id/complete-pickup") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/reservations/{id}/complete-pickup")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("completed" to true))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-complete-pickup",
                        requestSchema = "PickupCompleteRequest",
                        responseSchema = "ReservationResponse",
                        tag = "Reservations",
                        summary = "픽업 완료 처리",
                        pathParameters = listOf(parameterWithName("id").description("예약 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("completed")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("픽업 완료 여부 (필수)"),
                            ),
                        responseFields = reservationResponseFields,
                    ),
                )
            }
    }

    // ── 10. 예약 → 매출 전환 ───────────────────────────────────────────────────

    @Test
    fun `예약 매출 전환 문서화`() {
        val token = signupAndToken()
        val id = createReservation(token)

        mockMvc
            .post("/reservations/$id/convert-to-sale") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/reservations/{id}/convert-to-sale")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to "2026-06-01",
                            "productCategory" to "basic_bouquet",
                            "amount" to 100_000,
                            "paymentMethod" to "cash",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-convert-to-sale",
                        requestSchema = "SaleCreateRequest",
                        responseSchema = "SaleResponse",
                        tag = "Reservations",
                        summary = "예약 → 매출 전환 (매출 생성 후 예약에 saleId 연결)",
                        pathParameters = listOf(parameterWithName("id").description("예약 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .description("매출 발생일 (yyyy-MM-dd, 필수)"),
                                fieldWithPath("productCategory")
                                    .type(JsonFieldType.STRING)
                                    .description("상품 카테고리 (필수)"),
                                fieldWithPath("amount")
                                    .type(JsonFieldType.NUMBER)
                                    .description("결제 금액(원, 0 이상, 필수)"),
                                fieldWithPath("paymentMethod")
                                    .type(JsonFieldType.STRING)
                                    .description("결제방식. cash | card | transfer | naverpay | kakaopay | unpaid (필수)"),
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
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결할 고객 ID (본인 소유 검증)"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("비고"),
                            ),
                        responseFields = saleResponseFields,
                    ),
                )
            }
    }

    // ── 11. 매출에 픽업 추가 ───────────────────────────────────────────────────

    @Test
    fun `매출에 픽업 추가 문서화`() {
        val token = signupAndToken()

        // 픽업 추가를 위한 기준 매출 생성
        val saleRes =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-06-01",
                                "productCategory" to "basket",
                                "amount" to 50_000,
                                "paymentMethod" to "cash",
                                "customerName" to "이민준",
                                "customerPhone" to "010-9876-5432",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val saleId = objectMapper.readTree(saleRes).get("id").asText()

        mockMvc
            .post("/reservations/add-pickup/$saleId") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/reservations/add-pickup/{saleId}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to "2026-06-05",
                            "title" to "픽업",
                            "amount" to 0,
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-add-pickup",
                        requestSchema = "AddPickupRequest",
                        responseSchema = "ReservationResponse",
                        tag = "Reservations",
                        summary = "매출에 픽업 추가 (고객 정보는 매출에서 상속)",
                        pathParameters = listOf(parameterWithName("saleId").description("매출 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("date")
                                    .type(JsonFieldType.STRING)
                                    .description("픽업일 (yyyy-MM-dd, 필수)"),
                                fieldWithPath("time")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("픽업 시각 (HH:mm:ss)"),
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .description("픽업 제목 (필수)"),
                                fieldWithPath("amount")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("픽업 금액(원, 기본값 0)"),
                                fieldWithPath("reminderAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("리마인더 발송 예정 시각 (ISO-8601)"),
                            ),
                        responseFields = reservationResponseFields,
                    ),
                )
            }
    }

    // ── 12. 예약 삭제 ──────────────────────────────────────────────────────────

    @Test
    fun `예약 삭제 문서화`() {
        val token = signupAndToken()
        val id = createReservation(token)

        mockMvc
            .delete("/reservations/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/reservations/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "reservation-delete",
                        tag = "Reservations",
                        summary = "예약 삭제",
                        pathParameters = listOf(parameterWithName("id").description("예약 ID")),
                    ),
                )
            }
    }
}
