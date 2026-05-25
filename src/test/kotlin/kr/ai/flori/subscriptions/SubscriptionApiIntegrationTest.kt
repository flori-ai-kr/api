package kr.ai.flori.subscriptions

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

private const val SECRET = "test-webhook-secret-xyz"

/**
 * 구독 API HTTP 흐름: 웹훅 상태전이 + 게이팅 403 + user_id 격리 + 웹훅 인증(실제 보안필터·실제 PostgreSQL).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["revenuecat.webhook-secret=test-webhook-secret-xyz"])
@AutoConfigureMockMvc
class SubscriptionApiIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private data class Account(
        val token: String,
        val userId: String,
    )

    private fun signup(): Account {
        val signupBody =
            mockMvc
                .post("/auth/signup") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        objectMapper.writeValueAsString(
                            mapOf("email" to "sub-${UUID.randomUUID()}@flori.dev", "password" to "password123"),
                        )
                }.andReturn()
                .response.contentAsString
        val token = objectMapper.readTree(signupBody).get("accessToken").asText()
        val meBody =
            mockMvc
                .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        return Account(token, objectMapper.readTree(meBody).get("id").asText())
    }

    private fun eventBody(
        type: String,
        appUserId: String?,
    ): Map<String, Any?> =
        mapOf(
            "type" to type,
            "app_user_id" to appUserId,
            "product_id" to "premium_monthly",
            "store" to "APP_STORE",
            "entitlement_ids" to listOf("premium"),
            "expiration_at_ms" to System.currentTimeMillis() + 30L * 24 * 3600 * 1000,
        )

    private fun webhook(
        type: String,
        appUserId: String?,
        secret: String = SECRET,
    ) = mockMvc.post("/webhooks/revenuecat") {
        header(HttpHeaders.AUTHORIZATION, "Bearer $secret")
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("event" to eventBody(type, appUserId)))
    }

    private fun subscription(token: String) = mockMvc.get("/subscription") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

    @Test
    fun `구매 웹훅 후 구독이 active로 조회된다`() {
        val acc = signup()
        webhook("INITIAL_PURCHASE", acc.userId).andExpect {
            status { isOk() }
            jsonPath("$.received") { value(true) }
        }
        subscription(acc.token).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("active") }
            jsonPath("$.active") { value(true) }
            jsonPath("$.entitlement") { value("premium") }
            jsonPath("$.store") { value("apple") }
        }
    }

    @Test
    fun `이벤트 타입별 상태 전이`() {
        val acc = signup()
        webhook("INITIAL_PURCHASE", acc.userId).andExpect { status { isOk() } }

        webhook("BILLING_ISSUE", acc.userId).andExpect { status { isOk() } }
        subscription(acc.token).andExpect {
            jsonPath("$.status") { value("in_grace") }
            jsonPath("$.active") { value(true) }
        }

        webhook("EXPIRATION", acc.userId).andExpect { status { isOk() } }
        subscription(acc.token).andExpect {
            jsonPath("$.status") { value("expired") }
            jsonPath("$.active") { value(false) }
        }

        webhook("REFUND", acc.userId).andExpect { status { isOk() } }
        subscription(acc.token).andExpect {
            jsonPath("$.status") { value("none") }
            jsonPath("$.active") { value(false) }
        }
    }

    @Test
    fun `취소 이벤트는 active를 유지한다`() {
        val acc = signup()
        webhook("INITIAL_PURCHASE", acc.userId).andExpect { status { isOk() } }
        webhook("CANCELLATION", acc.userId).andExpect { status { isOk() } }
        subscription(acc.token).andExpect { jsonPath("$.status") { value("active") } }
    }

    @Test
    fun `구독 이력이 없으면 none을 반환한다`() {
        val acc = signup()
        subscription(acc.token).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("none") }
            jsonPath("$.active") { value(false) }
        }
    }

    @Test
    fun `프리미엄 엔드포인트는 비구독 403, 구독 시 200`() {
        val acc = signup()
        mockMvc
            .get("/subscription/premium-example") { header(HttpHeaders.AUTHORIZATION, "Bearer ${acc.token}") }
            .andExpect { status { isForbidden() } }

        webhook("INITIAL_PURCHASE", acc.userId).andExpect { status { isOk() } }

        mockMvc
            .get("/subscription/premium-example") { header(HttpHeaders.AUTHORIZATION, "Bearer ${acc.token}") }
            .andExpect {
                status { isOk() }
                jsonPath("$.message") { exists() }
            }
    }

    @Test
    fun `웹훅 시크릿이 틀리거나 없으면 401`() {
        val acc = signup()
        webhook("INITIAL_PURCHASE", acc.userId, secret = "wrong-secret").andExpect { status { isUnauthorized() } }

        mockMvc
            .post("/webhooks/revenuecat") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("event" to eventBody("INITIAL_PURCHASE", acc.userId)))
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `웹훅은 user_id로 격리된다`() {
        val owner = signup()
        val other = signup()
        webhook("INITIAL_PURCHASE", owner.userId).andExpect { status { isOk() } }

        subscription(other.token).andExpect { jsonPath("$.status") { value("none") } }
        subscription(owner.token).andExpect { jsonPath("$.status") { value("active") } }
    }

    @Test
    fun `토큰 없이 구독 조회는 401`() {
        mockMvc.get("/subscription").andExpect { status { isUnauthorized() } }
    }
}
