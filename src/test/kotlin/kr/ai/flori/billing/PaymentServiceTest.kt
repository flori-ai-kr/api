package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.ApprovedPayment
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.ChargeOutcome
import kr.ai.flori.billing.service.PaymentService
import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class PaymentServiceTest {
    @Autowired lateinit var paymentService: PaymentService

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired lateinit var billingKeyRepository: BillingKeyRepository

    @Autowired lateinit var paymentHistoryRepository: PaymentHistoryRepository

    @MockitoBean lateinit var billingClient: BillingClient

    private val kst = ZoneId.of("Asia/Seoul")

    @AfterEach
    fun cleanup() {
        paymentHistoryRepository.deleteAllInBatch()
        subscriptionRepository.deleteAllInBatch()
        billingKeyRepository.deleteAllInBatch()
    }

    private fun seed(userId: Long): Subscription {
        billingKeyRepository.save(BillingKey(userId, "cust_$userId", "bk_$userId"))
        val now = Instant.now()
        return subscriptionRepository.save(
            Subscription(
                userId = userId,
                plan = "MONTHLY",
                status = "TRIALING",
                amount = 14900,
                nextBillingAt = now,
            ).apply {
                currentPeriodStart = now
                currentPeriodEnd = now
            },
        )
    }

    @Test
    fun `성공시 PAID 기록 + ACTIVE + 다음달 주기 전진`() {
        val sub = seed(101L)
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(ApprovedPayment("pay_1", "ord_1", "2026-07-08T04:00:00+09:00"))

        val outcome = paymentService.chargeOnce(sub)

        assertThat(outcome).isEqualTo(ChargeOutcome.SUCCESS)
        val updated = subscriptionRepository.findById(sub.id!!).get()
        assertThat(updated.status).isEqualTo("ACTIVE")
        assertThat(updated.nextBillingAt).isBetween(
            Instant.now().plus(27, ChronoUnit.DAYS),
            Instant.now().plus(32, ChronoUnit.DAYS),
        )
        assertThat(paymentHistoryRepository.findAll().count { it.status == "PAID" }).isEqualTo(1)
    }

    @Test
    fun `실패시 FAILED 기록 + 구독 상태 불변 + 예외 안던짐`() {
        val sub = seed(102L)
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenThrow(AppException(BillingErrorCode.PAYMENT_REJECTED))

        val outcome = paymentService.chargeOnce(sub)

        assertThat(outcome).isEqualTo(ChargeOutcome.FAILED)
        val updated = subscriptionRepository.findById(sub.id!!).get()
        assertThat(updated.status).isEqualTo("TRIALING") // 상태 불변(dunning은 스케줄러)
        assertThat(paymentHistoryRepository.findAll().count { it.status == "FAILED" }).isEqualTo(1)
    }

    @Test
    fun `이미 PAID 주기면 멱등 skip`() {
        val sub = seed(103L)
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(ApprovedPayment("pay_1", "ord_1", null))
        paymentService.chargeOnce(sub)
        // 같은 주기 재호출(next_billing_at 동일) → skip
        val again = subscriptionRepository.findById(sub.id!!).get()
        again.nextBillingAt = sub.nextBillingAt // 같은 주기로 강제
        subscriptionRepository.save(again)

        val outcome = paymentService.chargeOnce(again)
        assertThat(outcome).isEqualTo(ChargeOutcome.ALREADY_PAID)
        assertThat(paymentHistoryRepository.findAll().count { it.status == "PAID" }).isEqualTo(1)
    }
}
