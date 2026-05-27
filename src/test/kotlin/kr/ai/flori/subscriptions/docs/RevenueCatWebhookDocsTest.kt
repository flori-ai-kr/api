package kr.ai.flori.subscriptions.docs

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * RevenueCatWebhookController RestDocs 문서화.
 *
 * 기본 컨텍스트에서는 `revenuecat.webhook-secret`이 빈 값이라 모든 웹훅이 401이 된다.
 * 따라서 InternalInsightDocsTest 패턴과 동일하게 별도 @SpringBootTest(properties)로 테스트 시크릿을 주입한다.
 * Authorization 헤더에 `Bearer <시크릿>` 형식으로 전송(SecurityConfig에서 /webhooks/{path} 는 공개이고,
 * 컨트롤러 내부에서 RevenueCatWebhookVerifier가 Bearer 값을 직접 검증한다).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["revenuecat.webhook-secret=test-revenuecat-secret"])
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class RevenueCatWebhookDocsTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    /** 테스트용 웹훅 시크릿 (SpringBootTest properties 주입값과 동일) */
    private val webhookSecret = "test-revenuecat-secret"

    private fun json(value: Any): String = objectMapper.writeValueAsString(value)

    /** docs() 헬퍼 — RestDocsSupport와 동일한 패턴 */
    private fun docs(
        identifier: String,
        tag: String,
        summary: String,
        requestFields: List<FieldDescriptor> = emptyList(),
        responseFields: List<FieldDescriptor> = emptyList(),
    ): ResultHandler {
        val params = ResourceSnippetParameters.builder().tag(tag).summary(summary)
        if (requestFields.isNotEmpty()) params.requestFields(*requestFields.toTypedArray())
        if (responseFields.isNotEmpty()) params.responseFields(*responseFields.toTypedArray())
        return MockMvcRestDocumentationWrapper.document(identifier, snippets = arrayOf(resource(params.build())))
    }

    /** 회원가입 후 userId 획득 (RevenueCat app_user_id로 사용) */
    private fun signupUserId(): String {
        val signupRes =
            mockMvc
                .post("/auth/signup") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(mapOf("email" to "webhook-docs-${UUID.randomUUID()}@flori.dev", "password" to "password123"))
                }.andReturn()
                .response.contentAsString
        val token = objectMapper.readTree(signupRes).get("accessToken").asText()
        val meRes =
            mockMvc
                .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(meRes).get("id").asText()
    }

    /** RevenueCat 웹훅 이벤트 페이로드 생성 */
    private fun webhookPayload(
        type: String,
        appUserId: String,
    ): Map<String, Any?> =
        mapOf(
            "event" to
                mapOf(
                    "type" to type,
                    "app_user_id" to appUserId,
                    "product_id" to "premium_monthly",
                    "store" to "APP_STORE",
                    "entitlement_ids" to listOf("premium"),
                    "entitlement_id" to "premium",
                    "expiration_at_ms" to (System.currentTimeMillis() + 30L * 24 * 3600 * 1000),
                    "original_transaction_id" to "txn_${UUID.randomUUID()}",
                    "id" to UUID.randomUUID().toString(),
                    "event_timestamp_ms" to System.currentTimeMillis(),
                ),
        )

    // ── RevenueCat 구독 웹훅 ─────────────────────────────────────────────────

    @Test
    fun `RevenueCat 구독 웹훅 문서화`() {
        val appUserId = signupUserId()

        mockMvc
            .post("/webhooks/revenuecat") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $webhookSecret")
                contentType = MediaType.APPLICATION_JSON
                content = json(webhookPayload("INITIAL_PURCHASE", appUserId))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "webhook-revenuecat",
                        tag = "Webhooks",
                        summary = "RevenueCat 구독 웹훅 (Bearer 시크릿 검증 후 이벤트 타입에 따라 구독 상태 갱신)",
                        requestFields =
                            listOf(
                                fieldWithPath("event")
                                    .type(JsonFieldType.OBJECT)
                                    .description("RevenueCat 웹훅 이벤트 객체 (필수)"),
                                fieldWithPath("event.type")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description(
                                        "이벤트 타입. INITIAL_PURCHASE | RENEWAL | CANCELLATION | " +
                                            "BILLING_ISSUE | EXPIRATION | REFUND | UNCANCELLATION",
                                    ),
                                fieldWithPath("event.app_user_id")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("앱 사용자 ID (앱이 RevenueCat에 설정한 식별자, user_id)"),
                                fieldWithPath("event.product_id")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("구독 상품 ID (예: premium_monthly)"),
                                fieldWithPath("event.store")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("결제 스토어 (APP_STORE | PLAY_STORE)"),
                                fieldWithPath("event.entitlement_ids")
                                    .type(JsonFieldType.ARRAY)
                                    .optional()
                                    .description("권한 ID 목록 (예: [\"premium\"])"),
                                fieldWithPath("event.entitlement_id")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("단일 권한 ID (null 가능)"),
                                fieldWithPath("event.expiration_at_ms")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("구독 만료 시각 (Unix ms, null 가능)"),
                                fieldWithPath("event.original_transaction_id")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("원본 트랜잭션 ID (null 가능)"),
                                fieldWithPath("event.id")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("RevenueCat 이벤트 고유 ID (null 가능)"),
                                fieldWithPath("event.event_timestamp_ms")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("이벤트 발생 시각 (Unix ms, null 가능)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("received")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("웹훅 수신 확인 (항상 true — RevenueCat 재시도 방지)"),
                            ),
                    ),
                )
            }
    }
}
