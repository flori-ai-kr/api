package kr.ai.flori.subscriptions.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get

/**
 * SubscriptionController RestDocs 문서화.
 * JWT 인증. 신규 가입 직후 구독 이력이 없으면 status="none"으로 응답한다.
 */
class SubscriptionDocsTest : RestDocsSupport() {
    /** SubscriptionResponse 공통 필드 */
    private val subscriptionResponseFields =
        listOf(
            fieldWithPath("status")
                .type(JsonFieldType.STRING)
                .description("구독 상태. active | in_grace | expired | none"),
            fieldWithPath("active")
                .type(JsonFieldType.BOOLEAN)
                .description("[서버 계산 SSOT] 활성 여부 (active/in_grace이면 true)"),
            fieldWithPath("entitlement")
                .type(JsonFieldType.STRING)
                .optional()
                .description("구독 권한 식별자 (e.g. premium). 미구독이면 null"),
            fieldWithPath("store")
                .type(JsonFieldType.STRING)
                .optional()
                .description("결제 스토어 (apple | google). 미구독이면 null"),
            fieldWithPath("productId")
                .type(JsonFieldType.STRING)
                .optional()
                .description("구독 상품 ID. 미구독이면 null"),
            fieldWithPath("currentPeriodEnd")
                .type(JsonFieldType.STRING)
                .optional()
                .description("현재 구독 기간 만료 시각 (ISO-8601). 미구독이면 null"),
        )

    // ── 1. 현재 구독 상태 조회 ────────────────────────────────────────────────

    @Test
    fun `현재 구독 상태 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/subscription") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "subscription-current",
                        tag = "Subscription",
                        summary = "현재 구독 상태 조회 (active/in_grace/expired/none + 만료일/티어)",
                        responseFields = subscriptionResponseFields,
                    ),
                )
            }
    }
}
