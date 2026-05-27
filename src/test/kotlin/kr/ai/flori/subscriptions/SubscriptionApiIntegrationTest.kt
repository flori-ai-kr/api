package kr.ai.flori.subscriptions

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.subscriptions.gating.RequiresSubscription
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val SECRET = "test-webhook-secret-xyz"

/**
 * 구독 API HTTP 흐름: 웹훅 상태전이 + 게이팅(401/403/200) + user_id 격리 + 웹훅 인증(실제 보안필터·실제 PostgreSQL).
 *
 * 게이팅 검증은 운영 코드에 데모 엔드포인트를 두지 않기 위해, 테스트 전용 컨트롤러([GatedTestConfig.GatedTestController])를
 * @TestConfiguration + @Import로 이 SpringBootTest 컨텍스트에만 등록해 수행한다. 컴포넌트 스캔 대상이 아니고
 * RestDocs 문서화 테스트에서도 호출되지 않으므로 OpenAPI 스펙(open-api-3.0.1.json)에는 노출되지 않는다.
 * 인터셉터([SubscriptionWebConfig])는 경로 제한 없이 전체에 적용되므로 /__test__/gated 경로도 게이팅 대상이다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["revenuecat.webhook-secret=test-webhook-secret-xyz"])
@AutoConfigureMockMvc
@Import(SubscriptionApiIntegrationTest.GatedTestConfig::class)
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

    private fun gated(token: String? = null) =
        mockMvc.get("/__test__/gated") {
            token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }

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

    @Test
    fun `게이팅 엔드포인트는 토큰이 없으면 401`() {
        gated().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `미구독 사용자가 게이팅 엔드포인트 호출 시 403`() {
        val acc = signup()
        gated(acc.token).andExpect { status { isForbidden() } }
    }

    @Test
    fun `구독 활성화 후 게이팅 엔드포인트 호출 시 200`() {
        val acc = signup()
        webhook("INITIAL_PURCHASE", acc.userId).andExpect { status { isOk() } }

        gated(acc.token).andExpect {
            status { isOk() }
            content { string("gated-ok") }
        }
    }

    /**
     * 게이팅 검증 전용 컨트롤러. @RequiresSubscription이 붙은 GET 엔드포인트 하나로,
     * 실제 SubscriptionAccessInterceptor + 실제 SubscriptionService + 임베디드 DB 경로를 그대로 탄다.
     * 운영 코드가 아니며 RestDocs 문서화 대상도 아니므로 OpenAPI 스펙에 포함되지 않는다.
     */
    @TestConfiguration
    class GatedTestConfig {
        @Bean
        fun gatedTestController(): GatedTestController = GatedTestController()
    }

    @RestController
    class GatedTestController {
        @RequiresSubscription
        @GetMapping("/__test__/gated")
        fun gated(): String = "gated-ok"
    }
}
