package kr.ai.flori.settings.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Push 구독 API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * FCM 발송은 테스트 환경에서 비활성화되어 있으며, 토큰 저장(subscribe)과 조회(status)만 검증한다.
 */
class PushSubscriptionDocsTest : RestDocsSupport() {
    // ── 1. 푸시 구독 등록 ─────────────────────────────────────────────────────

    @Test
    fun `푸시 구독 등록 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/push/subscribe") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "endpoint" to "fcm-token-${System.nanoTime()}",
                            "p256dh" to "BNcRdreALRFXTkOOUHK1EtK2wtZ",
                            "auth" to "tBHItJI5svbpez7KI4CCXg",
                            "userAgent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)",
                        ),
                    )
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "push-subscribe",
                        tag = "Push",
                        summary = "푸시 구독 등록 (endpoint 기준 upsert — FCM 토큰)",
                        requestFields =
                            listOf(
                                fieldWithPath("endpoint")
                                    .type(JsonFieldType.STRING)
                                    .description("FCM 토큰 또는 Web Push endpoint (필수)"),
                                fieldWithPath("p256dh")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("Web Push p256dh 키 (Web Push 전용, FCM은 불필요)"),
                                fieldWithPath("auth")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("Web Push auth 시크릿 (Web Push 전용)"),
                                fieldWithPath("userAgent")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("기기 User-Agent (식별용, 선택)"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 푸시 구독 상태 조회 ────────────────────────────────────────────────

    @Test
    fun `푸시 구독 상태 조회 문서화`() {
        val token = signupAndToken()

        // 구독 등록 후 상태 조회
        mockMvc
            .post("/push/subscribe") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("endpoint" to "fcm-status-token-${System.nanoTime()}"))
            }.andReturn()

        mockMvc
            .get("/push/status") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "push-status",
                        tag = "Push",
                        summary = "푸시 구독 상태 조회 (활성 구독 존재 여부)",
                        responseFields =
                            listOf(
                                fieldWithPath("subscribed")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("활성 구독 토큰 보유 여부 (true = 구독 중)"),
                            ),
                    ),
                )
            }
    }

    // ── 3. 푸시 구독 해지 ─────────────────────────────────────────────────────

    @Test
    fun `푸시 구독 해지 문서화`() {
        val token = signupAndToken()
        val endpoint = "fcm-unsubscribe-token-${System.nanoTime()}"

        // 구독 등록 후 해지
        mockMvc
            .post("/push/subscribe") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("endpoint" to endpoint))
            }.andReturn()

        mockMvc
            .post("/push/unsubscribe") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("endpoint", endpoint)
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "push-unsubscribe",
                        tag = "Push",
                        summary = "푸시 구독 해지 (endpoint로 구독 토큰 삭제)",
                    ),
                )
            }
    }
}
