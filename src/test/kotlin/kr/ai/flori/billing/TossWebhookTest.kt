package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.client.TossPayment
import kr.ai.flori.billing.dto.TossWebhookData
import kr.ai.flori.billing.dto.TossWebhookEvent
import kr.ai.flori.billing.entity.PaymentHistory
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.billing.service.TossWebhookService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class TossWebhookTest {
    @Autowired lateinit var service: TossWebhookService

    @Autowired lateinit var paymentHistoryRepository: PaymentHistoryRepository

    @MockitoBean lateinit var billingClient: BillingClient

    @AfterEach fun cleanup() = paymentHistoryRepository.deleteAll()

    private fun paid(
        orderId: String,
        paymentKey: String,
    ) {
        paymentHistoryRepository.save(
            PaymentHistory(1L, 10L, orderId, LocalDate.of(2026, 7, 8), 14900, "PAID").apply {
                tossPaymentKey = paymentKey
            },
        )
    }

    @Test
    fun `취소 이벤트 재조회 결과 CANCELED면 payment_history CANCELED 동기화`() {
        paid("ord_1", "pay_1")
        Mockito.`when`(billingClient.getPayment("pay_1")).thenReturn(TossPayment("CANCELED", "ord_1"))

        service.handle(TossWebhookEvent("PAYMENT_STATUS_CHANGED", TossWebhookData("pay_1", "ord_1", "CANCELED")))

        assertThat(paymentHistoryRepository.findByOrderId("ord_1")!!.status).isEqualTo("CANCELED")
    }

    @Test
    fun `재조회 결과 여전히 DONE이면 변경 없음`() {
        paid("ord_2", "pay_2")
        Mockito.`when`(billingClient.getPayment("pay_2")).thenReturn(TossPayment("DONE", "ord_2"))

        service.handle(TossWebhookEvent("PAYMENT_STATUS_CHANGED", TossWebhookData("pay_2", "ord_2", "DONE")))

        assertThat(paymentHistoryRepository.findByOrderId("ord_2")!!.status).isEqualTo("PAID")
    }

    @Test
    fun `관련없는 이벤트는 무시(예외 없음)`() {
        service.handle(TossWebhookEvent("METHOD_UPDATED", TossWebhookData(null, null, null)))
        // 예외 없이 통과하면 성공
    }
}
