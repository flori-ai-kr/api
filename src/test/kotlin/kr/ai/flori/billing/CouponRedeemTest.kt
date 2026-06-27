package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.billing.entity.Coupon
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.CouponRedemptionRepository
import kr.ai.flori.billing.repository.CouponRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.CouponService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class CouponRedeemTest {
    @Autowired lateinit var couponService: CouponService

    @Autowired lateinit var couponRepository: CouponRepository

    @Autowired lateinit var redemptionRepository: CouponRedemptionRepository

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var tokenProvider: JwtTokenProvider

    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setup() {
        couponRepository.deleteAll()
    }

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun user(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun coupon(
        code: String,
        days: Int = 30,
    ): Coupon = couponRepository.save(Coupon(code = code, days = days).apply { status = "ACTIVE" })

    @Test
    fun `구독 있는 유저 redeem시 nextBillingAt 가 days만큼 밀린다`() {
        val userId = user()
        val base = Instant.now().plus(10, ChronoUnit.DAYS)
        subscriptionRepository.save(
            Subscription(userId, "MONTHLY", "TRIALING", 14900, base).apply { currentPeriodEnd = base },
        )
        coupon("FLORI30", 30)

        val res = couponService.redeem("FLORI30")

        assertThat(res.pending).isFalse()
        assertThat(res.grantedDays).isEqualTo(30)
        val sub = subscriptionRepository.findByUserId(userId)!!
        assertThat(sub.nextBillingAt).isBetween(base.plus(29, ChronoUnit.DAYS), base.plus(31, ChronoUnit.DAYS))
        assertThat(couponRepository.findByCode("FLORI30")!!.redeemedCount).isEqualTo(1)
        assertThat(redemptionRepository.findByUserIdAndSubscriptionIdIsNull(userId)).isEmpty()
    }

    @Test
    fun `구독 없는 유저 redeem은 pending(subscription_id null)`() {
        user()
        coupon("FLORI30")
        val res = couponService.redeem("FLORI30")
        assertThat(res.pending).isTrue()
        assertThat(res.nextBillingAt).isNull()
    }

    @Test
    fun `없는 코드는 COUPON_NOT_FOUND`() {
        user()
        assertThatThrownBy { couponService.redeem("NOPE") }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `1인 중복 사용 차단`() {
        user()
        coupon("FLORI30")
        couponService.redeem("FLORI30")
        assertThatThrownBy { couponService.redeem("FLORI30") }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `폐기(DISABLED) 쿠폰 거부`() {
        user()
        couponRepository.save(Coupon(code = "DEAD", days = 10).apply { status = "DISABLED" })
        assertThatThrownBy { couponService.redeem("DEAD") }.isInstanceOf(AppException::class.java)
    }
}
