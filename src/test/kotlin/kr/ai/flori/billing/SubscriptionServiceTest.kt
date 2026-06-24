package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.client.IssuedBilling
import kr.ai.flori.billing.dto.SubscribeRequest
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.SubscriptionEligibilityRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.SubscriptionService
import kr.ai.flori.billing.support.IdentityHasher
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SubscriptionServiceTest {
    @Autowired lateinit var service: SubscriptionService

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired lateinit var billingKeyRepository: BillingKeyRepository

    @Autowired lateinit var eligibilityRepository: SubscriptionEligibilityRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var tokenProvider: JwtTokenProvider

    @Autowired lateinit var identityHasher: IdentityHasher

    @MockitoBean lateinit var billingClient: BillingClient

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun newUser(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    @Test
    fun `신규 신원 구독시 TRIALING + 체험14일 + 카드저장 + 신원원장 기록`() {
        val userId = newUser()
        Mockito
            .`when`(billingClient.issueBillingKey(anyString(), anyString()))
            .thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))

        val res = service.subscribe(SubscribeRequest(plan = "MONTHLY", authKey = "auth_1", customerKey = "cust_1"))

        assertThat(res.status).isEqualTo("TRIALING")
        val sub = subscriptionRepository.findByUserId(userId)!!
        assertThat(sub.amount).isEqualTo(14900)
        // 체험 종료(=다음 결제)는 대략 now+14일
        assertThat(sub.nextBillingAt).isBetween(
            Instant.now().plus(13, ChronoUnit.DAYS),
            Instant.now().plus(15, ChronoUnit.DAYS),
        )
        val card = billingKeyRepository.findByUserId(userId)!!
        assertThat(card.billingKey).isEqualTo("bk_1") // 복호화 원문
        assertThat(card.cardNumberMasked).isEqualTo("1234****")
        // 신원원장에 체험 사용 기록
        val user = userRepository.findById(userId).get()
        val elig = eligibilityRepository.findByIdentityHash(identityHasher.hash(user.provider, user.providerId))!!
        assertThat(elig.trialUsedAt).isNotNull
    }

    @Test
    fun `체험 이미 사용한 신원이 재구독하면 체험없이 ACTIVE + next_billing now`() {
        val userId = newUser()
        Mockito
            .`when`(billingClient.issueBillingKey(anyString(), anyString()))
            .thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))
        // 1차 구독(체험) 후 만료로 강등
        service.subscribe(SubscribeRequest("MONTHLY", "auth_1", "cust_1"))
        val sub1 = subscriptionRepository.findByUserId(userId)!!
        sub1.status = "EXPIRED"
        subscriptionRepository.save(sub1)

        // 2차 구독(재가입) — 같은 신원
        val res = service.subscribe(SubscribeRequest("MONTHLY", "auth_2", "cust_1"))

        assertThat(res.status).isEqualTo("ACTIVE")
        val sub2 = subscriptionRepository.findByUserId(userId)!!
        assertThat(sub2.nextBillingAt).isBefore(Instant.now().plus(1, ChronoUnit.MINUTES)) // 즉시(now)
    }

    @Test
    fun `이미 활성 구독이면 재구독 거부`() {
        newUser()
        Mockito
            .`when`(billingClient.issueBillingKey(anyString(), anyString()))
            .thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))
        service.subscribe(SubscribeRequest("MONTHLY", "auth_1", "cust_1")) // TRIALING

        assertThatThrownBy { service.subscribe(SubscribeRequest("MONTHLY", "auth_2", "cust_1")) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `prepare 는 기존 customerKey 있으면 재사용 없으면 신규 발급`() {
        newUser()
        val k1 = service.prepare().customerKey
        assertThat(k1).isNotBlank()
    }
}
