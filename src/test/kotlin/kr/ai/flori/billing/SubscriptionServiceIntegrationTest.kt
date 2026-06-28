package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.client.IssuedBilling
import kr.ai.flori.billing.dto.CardChangeRequest
import kr.ai.flori.billing.dto.SubscribeRequest
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.SubscriptionService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.Fixtures
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

/** SubscriptionService 통합 테스트. HTTP 레이어(@Valid·인터셉터)는 별도 MockMvc 테스트로 커버 필요. */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SubscriptionServiceIntegrationTest {
    @Autowired lateinit var service: SubscriptionService

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var tokenProvider: JwtTokenProvider

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var businessVerificationRepository: BusinessVerificationRepository

    @MockitoBean lateinit var billingClient: BillingClient

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun verifiedUser(): Long {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        businessVerificationRepository.save(Fixtures.approvedVerification(userId))
        return userId
    }

    private fun subscribedUser(): Long {
        val userId = verifiedUser()
        Mockito
            .`when`(billingClient.issueBillingKey(anyString(), anyString()))
            .thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))
        service.subscribe(SubscribeRequest("MONTHLY", "auth_1", "cust_1"))
        return userId
    }

    @Test
    fun `me 는 구독+카드+결제내역(빈) 반환`() {
        subscribedUser()
        val me = service.me()
        assertThat(me.subscription).isNotNull
        assertThat(me.subscription!!.card?.numberMasked).isEqualTo("1234****")
        assertThat(me.recentPayments).isEmpty()
    }

    @Test
    fun `me trialEligible - 인증된 미구독자는 true`() {
        verifiedUser()
        assertThat(service.me().trialEligible).isTrue()
    }

    @Test
    fun `me trialEligible - 구독자는 false`() {
        subscribedUser()
        assertThat(service.me().trialEligible).isFalse()
    }

    @Test
    fun `me trialEligible - 미인증자는 false`() {
        TestTenants.bootstrap(authService, tokenProvider, userRepository)
        assertThat(service.me().trialEligible).isFalse()
    }

    @Test
    fun `cancel 은 cancel_at_period_end true, resume 은 false`() {
        val userId = subscribedUser()
        service.cancel()
        assertThat(subscriptionRepository.findByUserId(userId)!!.cancelAtPeriodEnd).isTrue()
        service.resume()
        assertThat(subscriptionRepository.findByUserId(userId)!!.cancelAtPeriodEnd).isFalse()
    }

    @Test
    fun `card 교체는 새 빌링키로 갱신`() {
        subscribedUser()
        Mockito
            .`when`(billingClient.issueBillingKey(anyString(), anyString()))
            .thenReturn(IssuedBilling("bk_2", "국민", "5678****", "신용"))
        service.changeCard(CardChangeRequest("auth_2", "cust_1"))
        val me = service.me()
        assertThat(me.subscription!!.card?.numberMasked).isEqualTo("5678****")
    }
}
