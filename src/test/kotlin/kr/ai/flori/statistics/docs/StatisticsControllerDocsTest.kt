package kr.ai.flori.statistics.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate

/**
 * StatisticsController RestDocs 문서화.
 * JWT 인증 — 분포·시계열이 비지 않도록 매출을 시드한다.
 * 분포/시계열 배열 항목 필드는 .optional()로 선언해 빈 배열에서도 검증을 통과한다.
 */
class StatisticsControllerDocsTest : RestDocsSupport() {
    private fun distributionFields(prefix: String) =
        listOf(
            fieldWithPath("$prefix.id").type(JsonFieldType.NUMBER).optional().description("그룹 ID (미지정/삭제 시 null)"),
            fieldWithPath("$prefix.label").type(JsonFieldType.STRING).optional().description("표시명 (null이면 '기타')"),
            fieldWithPath("$prefix.amount").type(JsonFieldType.NUMBER).optional().description("매출 합계(원)"),
            fieldWithPath("$prefix.count").type(JsonFieldType.NUMBER).optional().description("매출 건수"),
            fieldWithPath("$prefix.percentage").type(JsonFieldType.NUMBER).optional().description("전체 대비 비율(0–100)"),
        )

    private fun expenseDistributionFields(prefix: String) =
        listOf(
            fieldWithPath("$prefix.id").type(JsonFieldType.NUMBER).optional().description("카테고리 ID (미지정/삭제 시 null)"),
            fieldWithPath("$prefix.label").type(JsonFieldType.STRING).optional().description("표시명 (null이면 '기타')"),
            fieldWithPath("$prefix.amount").type(JsonFieldType.NUMBER).optional().description("지출 합계(원)"),
            fieldWithPath("$prefix.count").type(JsonFieldType.NUMBER).optional().description("지출 건수"),
            fieldWithPath("$prefix.percentage").type(JsonFieldType.NUMBER).optional().description("전체 대비 비율(0–100)"),
        )

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
                        ),
                    )
            }.andReturn()
        // 지출
        mockMvc
            .post("/expenses") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "date" to today,
                            "itemName" to "장미 도매",
                            "categoryId" to expenseCategoryId(token),
                            "unitPrice" to 30_000,
                            "quantity" to 1,
                            "paymentMethodId" to expensePaymentId(token),
                        ),
                    )
            }.andReturn()
    }

    @Test
    fun `매출 통계 문서화`() {
        val token = signupAndToken()
        seedData(token)
        val today = LocalDate.now().toString()

        mockMvc
            .get("/statistics/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("from", today)
                param("to", today)
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "statistics-sales",
                        responseSchema = "SalesStatisticsResponse",
                        tag = "Statistics",
                        summary = "매출 통계 (KPI + 일별 시계열 + 카테고리/결제수단/채널 분포, 미수 제외, 직전 동일 길이 기간 대비 증감)",
                        responseFields =
                            listOf(
                                fieldWithPath("kpi.totalAmount").type(JsonFieldType.NUMBER).description("총 매출액(원, 미수 제외)"),
                                fieldWithPath("kpi.totalAmountDeltaPct").type(JsonFieldType.NUMBER).description("총 매출 증감률(%, 직전 기간 대비)"),
                                fieldWithPath("kpi.count").type(JsonFieldType.NUMBER).description("매출 건수(미수 제외)"),
                                fieldWithPath("kpi.countDelta").type(JsonFieldType.NUMBER).description("매출 건수 증감(건, 직전 기간 대비)"),
                                fieldWithPath("kpi.avgOrderValue").type(JsonFieldType.NUMBER).description("객단가(원)"),
                                fieldWithPath("kpi.avgOrderValueDeltaPct").type(JsonFieldType.NUMBER).description("객단가 증감률(%, 직전 기간 대비)"),
                                fieldWithPath("kpi.unpaidBalance").type(JsonFieldType.NUMBER).description("미수 잔액(원)"),
                                fieldWithPath("kpi.unpaidCount").type(JsonFieldType.NUMBER).description("미수 건수"),
                                fieldWithPath("timeseries").type(JsonFieldType.ARRAY).description("일별 매출 시계열(미수 제외)"),
                                fieldWithPath("timeseries[].date").type(JsonFieldType.STRING).optional().description("일자 (yyyy-MM-dd)"),
                                fieldWithPath("timeseries[].amount").type(JsonFieldType.NUMBER).optional().description("해당일 매출 합계(원)"),
                                fieldWithPath("timeseries[].count").type(JsonFieldType.NUMBER).optional().description("해당일 매출 건수"),
                                fieldWithPath("categoryDistribution").type(JsonFieldType.ARRAY).description("카테고리별 분포"),
                            ) +
                                distributionFields("categoryDistribution[]") +
                                listOf(
                                    fieldWithPath("paymentDistribution").type(JsonFieldType.ARRAY).description("결제수단별 분포"),
                                ) +
                                distributionFields("paymentDistribution[]") +
                                listOf(
                                    fieldWithPath("channelDistribution").type(JsonFieldType.ARRAY).description("채널별 분포"),
                                ) +
                                distributionFields("channelDistribution[]"),
                    ),
                )
            }
    }

    @Test
    fun `지출 통계 문서화`() {
        val token = signupAndToken()
        seedData(token)
        val today = LocalDate.now().toString()

        mockMvc
            .get("/statistics/expenses") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("from", today)
                param("to", today)
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "statistics-expenses",
                        responseSchema = "ExpensesStatisticsResponse",
                        tag = "Statistics",
                        summary = "지출 통계 (KPI + 일별 지출·순이익 시계열 + 카테고리 분포, 직전 동일 길이 기간 대비 증감)",
                        responseFields =
                            listOf(
                                fieldWithPath("kpi.totalAmount").type(JsonFieldType.NUMBER).description("총 지출액(원)"),
                                fieldWithPath("kpi.totalAmountDeltaPct").type(JsonFieldType.NUMBER).description("총 지출 증감률(%, 직전 기간 대비)"),
                                fieldWithPath("kpi.count").type(JsonFieldType.NUMBER).description("지출 건수"),
                                fieldWithPath("kpi.countDelta").type(JsonFieldType.NUMBER).description("지출 건수 증감(건, 직전 기간 대비)"),
                                fieldWithPath(
                                    "kpi.expenseRatioPct",
                                ).type(JsonFieldType.NUMBER).description("매출 대비 지출 비율(%, 미수 제외 매출 기준, 매출 0이면 0)"),
                                fieldWithPath("kpi.netProfit").type(JsonFieldType.NUMBER).description("순이익(원, 미수 제외 매출 - 지출)"),
                                fieldWithPath("kpi.netProfitDeltaPct").type(JsonFieldType.NUMBER).description("순이익 증감률(%, 직전 기간 대비)"),
                                fieldWithPath("timeseries").type(JsonFieldType.ARRAY).description("일별 지출·순이익 시계열"),
                                fieldWithPath("timeseries[].date").type(JsonFieldType.STRING).optional().description("일자 (yyyy-MM-dd)"),
                                fieldWithPath("timeseries[].expense").type(JsonFieldType.NUMBER).optional().description("해당일 지출 합계(원)"),
                                fieldWithPath(
                                    "timeseries[].netProfit",
                                ).type(JsonFieldType.NUMBER).optional().description("해당일 순이익(원, 미수 제외 매출 - 지출)"),
                                fieldWithPath("categoryDistribution").type(JsonFieldType.ARRAY).description("카테고리별 지출 분포"),
                            ) + expenseDistributionFields("categoryDistribution[]"),
                    ),
                )
            }
    }
}
