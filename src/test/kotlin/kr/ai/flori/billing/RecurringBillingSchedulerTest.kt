package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.ApprovedPayment
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.RecurringBillingScheduler
import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class RecurringBillingSchedulerTest {
    @Autowired lateinit var scheduler: RecurringBillingScheduler

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired lateinit var billingKeyRepository: BillingKeyRepository

    @MockitoBean lateinit var billingClient: BillingClient

    private fun due(
        userId: Long,
        status: String,
        cancel: Boolean = false,
        graceDaysAgo: Long? = null,
        withCard: Boolean = true,
    ): Subscription {
        val cardId = if (withCard) billingKeyRepository.save(BillingKey(userId, "cust_$userId", "bk_$userId")).id else null
        val past = Instant.now().minus(1, ChronoUnit.HOURS)
        return subscriptionRepository.save(
            Subscription(userId, "MONTHLY", status, 14900, past).apply {
                billingKeyId = cardId
                currentPeriodStart = past
                currentPeriodEnd = past
                cancelAtPeriodEnd = cancel
                graceUntil = graceDaysAgo?.let { Instant.now().minus(it, ChronoUnit.DAYS) }
            },
        )
    }

    @Test
    fun `due 구독 결제 성공시 ACTIVE 전진`() {
        val sub = due(201L, "TRIALING")
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(ApprovedPayment("pay", "ord", null))
        scheduler.runDueCharges(Instant.now())
        assertThat(subscriptionRepository.findById(sub.id!!).get().status).isEqualTo("ACTIVE")
    }

    @Test
    fun `결제 실패시 IN_GRACE + grace_until 3일 설정`() {
        val sub = due(202L, "ACTIVE")
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenThrow(AppException(BillingErrorCode.PAYMENT_REJECTED))
        scheduler.runDueCharges(Instant.now())
        val updated = subscriptionRepository.findById(sub.id!!).get()
        assertThat(updated.status).isEqualTo("IN_GRACE")
        assertThat(updated.graceUntil).isNotNull
    }

    @Test
    fun `유예 지난 IN_GRACE 결제 실패시 EXPIRED`() {
        val sub = due(203L, "IN_GRACE", graceDaysAgo = 1L) // grace_until = 어제(이미 경과)
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenThrow(AppException(BillingErrorCode.PAYMENT_REJECTED))
        scheduler.runDueCharges(Instant.now())
        assertThat(subscriptionRepository.findById(sub.id!!).get().status).isEqualTo("EXPIRED")
    }

    @Test
    fun `해지예약 due 구독은 결제없이 EXPIRED`() {
        val sub = due(204L, "ACTIVE", cancel = true)
        scheduler.runDueCharges(Instant.now())
        assertThat(subscriptionRepository.findById(sub.id!!).get().status).isEqualTo("EXPIRED")
        Mockito.verifyNoInteractions(billingClient)
    }

    @Test
    fun `무카드(billingKeyId null) 체험 due 는 토스 호출없이 EXPIRED`() {
        val sub = due(205L, "TRIALING", withCard = false)
        scheduler.runDueCharges(Instant.now())
        assertThat(subscriptionRepository.findById(sub.id!!).get().status).isEqualTo("EXPIRED")
        Mockito.verifyNoInteractions(billingClient)
    }
}
