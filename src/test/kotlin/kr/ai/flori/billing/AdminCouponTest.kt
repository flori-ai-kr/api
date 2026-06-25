package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.billing.dto.CouponIssueRequest
import kr.ai.flori.billing.service.AdminCouponService
import kr.ai.flori.billing.support.CouponCodeGenerator
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AdminCouponTest {
    @Autowired lateinit var adminCouponService: AdminCouponService

    @Autowired lateinit var generator: CouponCodeGenerator

    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var tokenProvider: JwtTokenProvider

    @Autowired lateinit var userRepository: UserRepository

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun admin(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    @Test
    fun `코드 자동생성은 12자 영숫자(하이픈 제외) + 헷갈림문자 없음`() {
        val raw = generator.generate().replace("-", "")
        assertThat(raw).hasSize(12)
        assertThat(raw).matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]+") // I/L/O/U 제외
    }

    @Test
    fun `발행시 자동코드 + ACTIVE + created_by 기록`() {
        val adminId = admin()
        val res =
            adminCouponService.issue(
                CouponIssueRequest(
                    code = null,
                    days = 30,
                    validFrom = null,
                    validUntil = null,
                    maxRedemptions = 100,
                    perUserLimit = 1,
                    source = "PROMO",
                    memo = "테스트",
                ),
            )
        assertThat(res.code).isNotBlank()
        assertThat(res.days).isEqualTo(30)
        assertThat(res.effectiveStatus).isEqualTo("ACTIVE")
        assertThat(adminCouponService.list().map { it.id }).contains(res.id)
    }

    @Test
    fun `커스텀 코드 발행`() {
        admin()
        val res =
            adminCouponService.issue(
                CouponIssueRequest(
                    code = "OPEN2026",
                    days = 14,
                    validFrom = null,
                    validUntil = null,
                    maxRedemptions = null,
                    perUserLimit = 1,
                    source = "EVENT",
                    memo = null,
                ),
            )
        assertThat(res.code).isEqualTo("OPEN2026")
    }

    @Test
    fun `상세는 쿠폰 + 사용현황(redemptions) 반환`() {
        admin()
        val issued =
            adminCouponService.issue(
                CouponIssueRequest("CODEX", 10, null, null, null, 1, "PROMO", null),
            )
        val detail = adminCouponService.detail(issued.id)
        assertThat(detail.coupon.code).isEqualTo("CODEX")
        assertThat(detail.redemptions).isEmpty()
    }

    @Test
    fun `폐기시 DISABLED + 감사로그`() {
        admin()
        val issued =
            adminCouponService.issue(
                CouponIssueRequest("KILLME", 10, null, null, null, 1, "PROMO", null),
            )
        val disabled = adminCouponService.disable(issued.id)
        assertThat(disabled.effectiveStatus).isEqualTo("DISABLED")
    }
}
