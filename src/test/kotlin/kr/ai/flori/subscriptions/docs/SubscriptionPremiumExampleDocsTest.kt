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
 * GET /subscription/premium-example RestDocs 문서화.
 *
 * @RequiresSubscription 게이팅 엔드포인트 — 활성 구독(active/in_grace) 보유 사용자만 200 응답.
 * 구독 활성화는 RevenueCat 웹훅(INITIAL_PURCHASE)으로 상태를 seeding 한다.
 * RevenueCatWebhookDocsTest와 동일하게 revenuecat.webhook-secret 프로퍼티를 주입해 웹훅을 호출한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["revenuecat.webhook-secret=test-revenuecat-secret"])
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class SubscriptionPremiumExampleDocsTest {
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
        responseFields: List<FieldDescriptor> = emptyList(),
    ): ResultHandler {
        val params = ResourceSnippetParameters.builder().tag(tag).summary(summary)
        if (responseFields.isNotEmpty()) params.responseFields(*responseFields.toTypedArray())
        return MockMvcRestDocumentationWrapper.document(identifier, snippets = arrayOf(resource(params.build())))
    }

    /**
     * 회원가입 후 (access 토큰, userId) 쌍 반환.
     * userId 는 RevenueCat app_user_id 로 사용해 웹훅으로 구독을 seeding 한다.
     */
    private data class Account(
        val token: String,
        val userId: String,
    )

    private fun signupAccount(): Account {
        val signupRes =
            mockMvc
                .post("/auth/signup") {
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("email" to "premium-docs-${UUID.randomUUID()}@flori.dev", "password" to "password123"))
                }.andReturn()
                .response.contentAsString
        val token = objectMapper.readTree(signupRes).get("accessToken").asText()
        val meRes =
            mockMvc
                .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        val userId = objectMapper.readTree(meRes).get("id").asText()
        return Account(token, userId)
    }

    /**
     * RevenueCat INITIAL_PURCHASE 웹훅으로 사용자 구독을 active 상태로 seeding 한다.
     * Bearer 시크릿은 SpringBootTest properties로 주입한 값과 일치해야 한다.
     */
    private fun seedActiveSubscription(userId: String) {
        val payload =
            mapOf(
                "event" to
                    mapOf(
                        "type" to "INITIAL_PURCHASE",
                        "app_user_id" to userId,
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
        mockMvc
            .post("/webhooks/revenuecat") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $webhookSecret")
                contentType = MediaType.APPLICATION_JSON
                content = json(payload)
            }.andExpect { status { isOk() } }
    }

    // ── 구독 전용 예시 기능 (premium-example) ─────────────────────────────────

    @Test
    fun `구독 전용 예시 기능 문서화`() {
        // 회원가입 후 RevenueCat 웹훅으로 구독을 active 상태로 seeding
        val account = signupAccount()
        seedActiveSubscription(account.userId)

        mockMvc
            .get("/subscription/premium-example") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${account.token}")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "subscription-premium-example",
                        tag = "Subscription",
                        summary = "구독 전용 예시 기능 (@RequiresSubscription — 활성 구독 없으면 403)",
                        responseFields =
                            listOf(
                                fieldWithPath("message")
                                    .type(JsonFieldType.STRING)
                                    .description("프리미엄 기능 접근 허용 메시지"),
                            ),
                    ),
                )
            }
    }
}
