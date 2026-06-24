package kr.ai.flori.billing.client

import kr.ai.flori.billing.config.TossPaymentsProperties
import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestFactory

class BillingClientTest {
    private val props = TossPaymentsProperties(secretKey = "test_sk_abc", baseUrl = "https://api.tosspayments.com")

    private fun clientWith(): Pair<BillingClient, MockRestServiceServer> {
        // RestClient가 쓰는 RestTemplate 인프라에 MockRestServiceServer를 바인딩
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return BillingClient(props, builder) to server
    }

    @Test
    fun `빌링키 발급 성공`() {
        val (client, server) = clientWith()
        server.expect(requestTo("https://api.tosspayments.com/v1/billing/authorizations/issue"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"billingKey":"bk_123","card":{"company":"신한","number":"1234****","cardType":"체크"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val result = client.issueBillingKey(authKey = "auth_1", customerKey = "cust_1")
        assertThat(result.billingKey).isEqualTo("bk_123")
        assertThat(result.cardCompany).isEqualTo("신한")
        server.verify()
    }

    @Test
    fun `자동결제 승인 성공`() {
        val (client, server) = clientWith()
        server.expect(requestTo("https://api.tosspayments.com/v1/billing/bk_123"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"paymentKey":"pay_999","orderId":"sub1_20260708_a1","approvedAt":"2026-07-08T04:00:00+09:00"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val result = client.approveBilling(
            billingKey = "bk_123", customerKey = "cust_1", amount = 14900,
            orderId = "sub1_20260708_a1", orderName = "flori 월 구독", idempotencyKey = "sub1_20260708_a1",
        )
        assertThat(result.paymentKey).isEqualTo("pay_999")
        server.verify()
    }

    @Test
    fun `승인 거절(4xx)이면 PAYMENT_REJECTED 예외`() {
        val (client, server) = clientWith()
        server.expect(requestTo("https://api.tosspayments.com/v1/billing/bk_123"))
            .andRespond(
                withStatus(HttpStatus.FORBIDDEN)
                    .body("""{"code":"REJECT_CARD_COMPANY","message":"한도초과"}""")
                    .contentType(MediaType.APPLICATION_JSON),
            )
        assertThatThrownBy {
            client.approveBilling("bk_123", "cust_1", 14900, "o1", "flori 월 구독", "o1")
        }.isInstanceOf(AppException::class.java)
        server.verify()
    }
}
