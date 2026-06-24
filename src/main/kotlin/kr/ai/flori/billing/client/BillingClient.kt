package kr.ai.flori.billing.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kr.ai.flori.billing.config.TossPaymentsProperties
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.common.error.AppException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.Base64

/** 토스페이먼츠 빌링 API 캡슐화. secretKey Basic 인증, 거절/오류는 AppException으로 wrap. */
@Component
class BillingClient(
    private val properties: TossPaymentsProperties,
    builder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = builder.build()
    private val authHeader: String =
        "Basic " + Base64.getEncoder().encodeToString("${properties.secretKey}:".toByteArray())

    fun issueBillingKey(authKey: String, customerKey: String): IssuedBilling {
        val res = post(
            "/v1/billing/authorizations/issue",
            mapOf("authKey" to authKey, "customerKey" to customerKey),
            null,
            IssueResponse::class.java,
            BillingErrorCode.BILLING_KEY_ISSUE_FAILED,
        )
        return IssuedBilling(res.billingKey, res.card?.company, res.card?.number, res.card?.cardType)
    }

    fun approveBilling(
        billingKey: String,
        customerKey: String,
        amount: Int,
        orderId: String,
        orderName: String,
        idempotencyKey: String,
    ): ApprovedPayment {
        val res = post(
            "/v1/billing/$billingKey",
            mapOf("customerKey" to customerKey, "amount" to amount, "orderId" to orderId, "orderName" to orderName),
            idempotencyKey,
            ApproveResponse::class.java,
            BillingErrorCode.PAYMENT_REJECTED,
        )
        return ApprovedPayment(res.paymentKey, res.orderId, res.approvedAt)
    }

    private fun <T> post(
        path: String,
        body: Map<String, Any>,
        idempotencyKey: String?,
        responseType: Class<T>,
        onError: BillingErrorCode,
    ): T =
        try {
            restClient.post()
                .uri(properties.baseUrl + path)
                .headers { h ->
                    h.set(HttpHeaders.AUTHORIZATION, authHeader)
                    h.contentType = MediaType.APPLICATION_JSON
                    if (idempotencyKey != null) h.set("Idempotency-Key", idempotencyKey)
                }
                .body(body)
                .retrieve()
                .body(responseType) ?: throw AppException(onError)
        } catch (e: RestClientResponseException) {
            // 토스가 코드/메시지를 본문에 담아줌 — 사유는 로깅만(사용자 메시지는 기본값)
            log.warn("토스 빌링 호출 실패 path={} status={} body={}", path, e.statusCode, e.responseBodyAsString)
            throw AppException(onError, cause = e)
        }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class IssueResponse(val billingKey: String, val card: Card? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Card(val company: String? = null, val number: String? = null, val cardType: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ApproveResponse(val paymentKey: String, val orderId: String, val approvedAt: String? = null)
}

data class IssuedBilling(val billingKey: String, val cardCompany: String?, val cardNumber: String?, val cardType: String?)
data class ApprovedPayment(val paymentKey: String, val orderId: String, val approvedAt: String?)
