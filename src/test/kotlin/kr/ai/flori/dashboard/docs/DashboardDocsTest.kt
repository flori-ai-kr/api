package kr.ai.flori.dashboard.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.LocalDate

/**
 * DashboardController RestDocs 문서화.
 * JWT 인증 — 데이터 시드 후 집계 결과가 비어 있지 않도록 매출·지출·예약을 생성한다.
 * 배열 항목 필드는 .optional()로 선언해 빈 배열일 때도 검증을 통과한다.
 */
class DashboardDocsTest : RestDocsSupport() {
    /** 오늘/월 공통 매출 요약 필드 */
    private val summaryFields =
        listOf(
            fieldWithPath("summary.totalAmount").type(JsonFieldType.NUMBER).description("총 매출액(원, 미수 제외)"),
            fieldWithPath("summary.cardAmount").type(JsonFieldType.NUMBER).description("카드 결제 합계(원)"),
            fieldWithPath("summary.cashAmount").type(JsonFieldType.NUMBER).description("현금 결제 합계(원)"),
            fieldWithPath("summary.transferAmount").type(JsonFieldType.NUMBER).description("계좌이체 합계(원)"),
            fieldWithPath("summary.naverpayAmount").type(JsonFieldType.NUMBER).description("네이버페이 합계(원)"),
            fieldWithPath("summary.kakaopayAmount").type(JsonFieldType.NUMBER).description("카카오페이 합계(원)"),
        )

    /** SaleResponse 인라인 필드 — 배열 항목은 모두 optional() (빈 배열 대응) */
    private fun saleFields(prefix: String) =
        listOf(
            fieldWithPath("$prefix.id").type(JsonFieldType.NUMBER).optional().description("매출 ID"),
            fieldWithPath("$prefix.date").type(JsonFieldType.STRING).optional().description("매출 발생일 (yyyy-MM-dd)"),
            fieldWithPath("$prefix.categoryId").type(JsonFieldType.NUMBER).optional().description("상품 카테고리 ID (null 가능)"),
            fieldWithPath("$prefix.categoryLabel").type(JsonFieldType.STRING).optional().description("상품 카테고리 이름 (null 가능)"),
            fieldWithPath("$prefix.amount").type(JsonFieldType.NUMBER).optional().description("결제 금액(원)"),
            fieldWithPath("$prefix.paymentMethodId").type(JsonFieldType.NUMBER).optional().description("결제수단 ID (미수면 null)"),
            fieldWithPath("$prefix.paymentMethodLabel").type(JsonFieldType.STRING).optional().description("결제수단 이름 (미수면 null)"),
            fieldWithPath("$prefix.channelId").type(JsonFieldType.NUMBER).optional().description("매출 채널 ID (null 가능)"),
            fieldWithPath("$prefix.channelLabel").type(JsonFieldType.STRING).optional().description("매출 채널 이름 (null 가능)"),
            fieldWithPath("$prefix.customerName").type(JsonFieldType.STRING).optional().description("고객명"),
            fieldWithPath("$prefix.customerPhone").type(JsonFieldType.STRING).optional().description("고객 전화번호"),
            fieldWithPath("$prefix.customerId").type(JsonFieldType.NUMBER).optional().description("연결된 고객 ID"),
            fieldWithPath("$prefix.memo").type(JsonFieldType.STRING).optional().description("비고"),
            fieldWithPath("$prefix.isUnpaid").type(JsonFieldType.BOOLEAN).optional().description("미수 여부"),
            fieldWithPath("$prefix.hasReview").type(JsonFieldType.BOOLEAN).optional().description("리뷰 보유 여부"),
            fieldWithPath("$prefix.photos").type(JsonFieldType.ARRAY).optional().description("연결된 사진 URL 목록 (없으면 빈 배열)"),
            fieldWithPath("$prefix.createdAt").type(JsonFieldType.STRING).optional().description("생성 시각 (ISO-8601)"),
            fieldWithPath("$prefix.updatedAt").type(JsonFieldType.STRING).optional().description("최종 수정 시각 (ISO-8601)"),
        )

    /** ReservationResponse 인라인 필드 — 배열 항목은 모두 optional() (빈 배열 대응) */
    private fun reservationFields(prefix: String) =
        listOf(
            fieldWithPath("$prefix.id").type(JsonFieldType.NUMBER).optional().description("예약 ID"),
            fieldWithPath("$prefix.date").type(JsonFieldType.STRING).optional().description("예약 날짜 (yyyy-MM-dd)"),
            fieldWithPath("$prefix.time").type(JsonFieldType.STRING).optional().description("예약 시간 (HH:mm:ss, null 가능)"),
            fieldWithPath("$prefix.customerName").type(JsonFieldType.STRING).optional().description("고객명"),
            fieldWithPath("$prefix.customerPhone").type(JsonFieldType.STRING).optional().description("고객 전화번호 (null 가능)"),
            fieldWithPath("$prefix.title").type(JsonFieldType.STRING).optional().description("예약 제목"),
            fieldWithPath("$prefix.memo").type(JsonFieldType.STRING).optional().description("메모 (null 가능)"),
            fieldWithPath("$prefix.status").type(JsonFieldType.STRING).optional().description("예약 상태"),
            fieldWithPath("$prefix.saleId").type(JsonFieldType.NUMBER).optional().description("연결된 매출 ID (null 가능)"),
            fieldWithPath("$prefix.amount").type(JsonFieldType.NUMBER).optional().description("예약 금액(원)"),
            fieldWithPath("$prefix.reminderAt").type(JsonFieldType.STRING).optional().description("리마인더 시각 (ISO-8601, null 가능)"),
            fieldWithPath("$prefix.reminderSent").type(JsonFieldType.BOOLEAN).optional().description("리마인더 전송 여부"),
            fieldWithPath("$prefix.pickupCompleted").type(JsonFieldType.BOOLEAN).optional().description("픽업 완료 여부"),
            fieldWithPath("$prefix.saleDate").type(JsonFieldType.STRING).optional().description("연결 매출 결제일 (null 가능)"),
            fieldWithPath("$prefix.productCategory").type(JsonFieldType.STRING).optional().description("연결 매출 카테고리 이름 (null 가능)"),
            fieldWithPath("$prefix.customerId").type(JsonFieldType.NUMBER).optional().description("연결 고객 ID (null 가능)"),
            fieldWithPath("$prefix.purchaseCount").type(JsonFieldType.NUMBER).optional().description("고객 누적 구매 건수 (null 가능)"),
            fieldWithPath("$prefix.saleIsUnpaid").type(JsonFieldType.BOOLEAN).optional().description("연결 매출 미수 여부 (null 가능)"),
            fieldWithPath("$prefix.salePaymentMethod").type(JsonFieldType.STRING).optional().description("연결 매출 결제방식 (null 가능)"),
            fieldWithPath("$prefix.saleReservationChannel").type(JsonFieldType.STRING).optional().description("연결 매출 채널 이름 (null 가능)"),
            fieldWithPath("$prefix.createdAt").type(JsonFieldType.STRING).optional().description("생성 시각 (ISO-8601)"),
            fieldWithPath("$prefix.updatedAt").type(JsonFieldType.STRING).optional().description("최종 수정 시각 (ISO-8601)"),
        )

    /**
     * 테스트용 데이터 시드.
     * - 카드/현금 매출 각 1건
     * - 지출 1건 (당월 집계 확인용)
     * - 예약 1건 (upcomingReservations 포함용)
     * - 예약 1건 (과거 reminderAt 설정 → triggeredReminders 포함용)
     */
    private fun seedData(token: String) {
        val today = LocalDate.now().toString()

        // 카드 매출
        mockMvc
            .post("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to today,
                            "categoryId" to saleCategoryId(token),
                            "amount" to 100_000,
                            "paymentMethodId" to salePaymentId(token),
                            "channelId" to saleChannelId(token),
                            "customerPhone" to "01012345678",
                        ),
                    )
            }.andReturn()

        // 현금 매출
        mockMvc
            .post("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to today,
                            "categoryId" to saleCategoryId(token, "vase"),
                            "amount" to 50_000,
                            "paymentMethodId" to salePaymentId(token, "cash"),
                            "channelId" to saleChannelId(token, "phone"),
                        ),
                    )
            }.andReturn()

        // 지출 (당월)
        mockMvc
            .post("/expenses") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to today,
                            "itemName" to "장미",
                            "categoryId" to expenseCategoryId(token),
                            "unitPrice" to 5_000,
                            "quantity" to 2,
                            "paymentMethodId" to expensePaymentId(token),
                        ),
                    )
            }.andReturn()

        // 일반 예약 (오늘, upcomingReservations에 포함)
        mockMvc
            .post("/reservations") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to today,
                            "customerName" to "김하늘",
                            "title" to "웨딩 부케",
                            "amount" to 150_000,
                        ),
                    )
            }.andReturn()

        // 리마인더 발동 예약 (reminderAt=과거 → triggeredReminders에 포함)
        mockMvc
            .post("/reservations") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to today,
                            "customerName" to "박민준",
                            "title" to "생일 꽃다발",
                            "amount" to 80_000,
                            "reminderAt" to Instant.now().minusSeconds(60).toString(),
                        ),
                    )
            }.andReturn()
    }

    // ── 1. 오늘 대시보드 ─────────────────────────────────────────────────────

    @Test
    fun `오늘 대시보드 문서화`() {
        val token = signupAndToken()
        seedData(token)

        mockMvc
            .get("/dashboard/today") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "dashboard-today",
                        responseSchema = "TodayDashboardResponse",
                        tag = "Dashboard",
                        summary = "오늘 대시보드 (매출 요약 + 다가오는 예약 + 발동 리마인더 + 최근 매출 + 카테고리)",
                        responseFields =
                            summaryFields +
                                listOf(
                                    fieldWithPath("upcomingReservations")
                                        .type(JsonFieldType.ARRAY)
                                        .description("오늘 이후 다가오는 예약 목록"),
                                ) +
                                reservationFields("upcomingReservations[]") +
                                listOf(
                                    fieldWithPath("triggeredReminders")
                                        .type(JsonFieldType.ARRAY)
                                        .description("발동된 리마인더 예약 목록 (reminderAt 경과 + 미전송)"),
                                ) +
                                reservationFields("triggeredReminders[]") +
                                listOf(
                                    fieldWithPath("recentSales")
                                        .type(JsonFieldType.ARRAY)
                                        .description("최근 매출 목록"),
                                ) +
                                saleFields("recentSales[]") +
                                listOf(
                                    fieldWithPath("saleCategories")
                                        .type(JsonFieldType.ARRAY)
                                        .description("매출 카테고리 옵션 목록"),
                                    fieldWithPath("saleCategories[].value")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("카테고리 값 (식별자)"),
                                    fieldWithPath("saleCategories[].label")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("카테고리 표시명"),
                                ),
                    ),
                )
            }
    }

    // ── 2. 월 통계 ────────────────────────────────────────────────────────────

    @Test
    fun `월 통계 문서화`() {
        val token = signupAndToken()
        seedData(token)

        mockMvc
            .get("/dashboard/month") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param(
                    "month",
                    java.time.YearMonth
                        .now()
                        .toString(),
                )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "dashboard-month",
                        responseSchema = "MonthDashboardResponse",
                        tag = "Dashboard",
                        summary = "월 통계 (매출/지출 요약 + 카테고리/결제수단/채널/고객/지출 통계, 네이티브 SQL 집계)",
                        responseFields =
                            summaryFields +
                                listOf(
                                    fieldWithPath("expenseTotal")
                                        .type(JsonFieldType.NUMBER)
                                        .description("월 총 지출액(원)"),
                                    fieldWithPath("categoryStats")
                                        .type(JsonFieldType.ARRAY)
                                        .description("카테고리별 통계 목록"),
                                    fieldWithPath("categoryStats[].categoryId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("카테고리 ID (미지정/삭제 시 null)"),
                                    fieldWithPath("categoryStats[].label")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("카테고리 표시명 (null이면 '기타')"),
                                    fieldWithPath("categoryStats[].count")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("매출 건수"),
                                    fieldWithPath("categoryStats[].amount")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("매출 합계(원)"),
                                    fieldWithPath("categoryStats[].percentage")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("전체 대비 비율(0–100)"),
                                    fieldWithPath("paymentStats")
                                        .type(JsonFieldType.ARRAY)
                                        .description("결제수단별 통계 목록"),
                                    fieldWithPath("paymentStats[].paymentMethodId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("결제수단 값(식별자)"),
                                    fieldWithPath("paymentStats[].label")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("결제수단 표시명"),
                                    fieldWithPath("paymentStats[].count")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("매출 건수"),
                                    fieldWithPath("paymentStats[].amount")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("매출 합계(원)"),
                                    fieldWithPath("paymentStats[].percentage")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("전체 대비 비율(0–100)"),
                                    fieldWithPath("channelStats")
                                        .type(JsonFieldType.ARRAY)
                                        .description("예약 채널별 통계 목록"),
                                    fieldWithPath("channelStats[].channelId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("채널 ID (미지정/삭제 시 null)"),
                                    fieldWithPath("channelStats[].label")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("채널 표시명"),
                                    fieldWithPath("channelStats[].count")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("매출 건수"),
                                    fieldWithPath("channelStats[].amount")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("매출 합계(원)"),
                                    fieldWithPath("channelStats[].percentage")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("전체 대비 비율(0–100)"),
                                    fieldWithPath("customerStats.totalCustomers")
                                        .type(JsonFieldType.NUMBER)
                                        .description("이번 달 구매 고객 수"),
                                    fieldWithPath("customerStats.returningCustomers")
                                        .type(JsonFieldType.NUMBER)
                                        .description("재방문 고객 수"),
                                    fieldWithPath("customerStats.newCustomers")
                                        .type(JsonFieldType.NUMBER)
                                        .description("신규 고객 수"),
                                    fieldWithPath("expenseStats")
                                        .type(JsonFieldType.ARRAY)
                                        .description("지출 카테고리별 통계 목록"),
                                    fieldWithPath("expenseStats[].categoryId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("지출 카테고리 ID (미지정/삭제 시 null)"),
                                    fieldWithPath("expenseStats[].label")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("지출 카테고리 표시명"),
                                    fieldWithPath("expenseStats[].amount")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("지출 합계(원)"),
                                    fieldWithPath("expenseStats[].percentage")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("전체 지출 대비 비율(0–100)"),
                                ),
                    ),
                )
            }
    }
}
