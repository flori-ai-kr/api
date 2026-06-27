package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import jakarta.validation.Validation
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.billing.dto.CouponIssueRequest
import kr.ai.flori.billing.dto.CouponUpdateRequest
import kr.ai.flori.billing.entity.CouponRedemption
import kr.ai.flori.billing.repository.CouponRedemptionRepository
import kr.ai.flori.billing.service.AdminCouponService
import kr.ai.flori.billing.support.CouponCodeGenerator
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AdminCouponTest {
    @Autowired lateinit var adminCouponService: AdminCouponService

    @Autowired lateinit var generator: CouponCodeGenerator

    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var tokenProvider: JwtTokenProvider

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var redemptionRepository: CouponRedemptionRepository

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
                CouponIssueRequest("CODEX", 10, null, null, null, "PROMO", null),
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
                CouponIssueRequest("KILLME", 10, null, null, null, "PROMO", null),
            )
        val disabled = adminCouponService.disable(issued.id)
        assertThat(disabled.effectiveStatus).isEqualTo("DISABLED")
    }

    @Test
    fun `상세 사용현황은 닉네임·가게명을 배치로 채운다`() {
        admin()
        val issued =
            adminCouponService.issue(
                CouponIssueRequest("FILLED", 10, null, null, null, "PROMO", null),
            )
        // 별도 점주 가입(닉네임/가게명 고유) 후 그 유저의 사용기록 1건 적재
        val nick = "사장님-FILLED-${UUID.randomUUID()}"
        val store = "플로리 플라워샵"
        val redeemerEmail = "redeemer-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, redeemerEmail, nickname = nick, storeName = store)
        val redeemerId = requireNotNull(requireNotNull(userRepository.findByEmail(redeemerEmail)).id)
        redemptionRepository.save(CouponRedemption(couponId = issued.id, userId = redeemerId, grantedDays = 10))

        val detail = adminCouponService.detail(issued.id)

        assertThat(detail.redemptions).hasSize(1)
        val row = detail.redemptions.first()
        assertThat(row.userId).isEqualTo(redeemerId)
        assertThat(row.grantedDays).isEqualTo(10)
        assertThat(row.nickname).isEqualTo(nick)
        assertThat(row.storeName).isEqualTo(store)
    }

    @Test
    fun `수정은 days·기간·한도·메모를 갱신하고 code·source는 불변`() {
        admin()
        val issued =
            adminCouponService.issue(
                CouponIssueRequest("EDITME", 10, null, null, 5, "EVENT", "old"),
            )
        val from = Instant.parse("2026-07-01T00:00:00Z")
        val until = Instant.parse("2026-08-01T00:00:00Z")

        val updated =
            adminCouponService.update(
                issued.id,
                CouponUpdateRequest(
                    days = 20,
                    validFrom = from,
                    validUntil = until,
                    maxRedemptions = 50,
                    memo = "new",
                ),
            )

        assertThat(updated.days).isEqualTo(20)
        assertThat(updated.validFrom).isEqualTo(from)
        assertThat(updated.validUntil).isEqualTo(until)
        assertThat(updated.maxRedemptions).isEqualTo(50)
        assertThat(updated.memo).isEqualTo("new")
        // code·source는 불변(식별자/용도)
        assertThat(updated.code).isEqualTo("EDITME")
        assertThat(updated.source).isEqualTo("EVENT")
        // 영속화 확인
        assertThat(adminCouponService.detail(issued.id).coupon.days).isEqualTo(20)
    }

    @Test
    fun `없는 쿠폰 수정은 NOT_FOUND`() {
        admin()
        assertThatThrownBy {
            adminCouponService.update(999_999L, CouponUpdateRequest(days = 5))
        }.isInstanceOf(AppException::class.java)
            .extracting { (it as AppException).errorCode }
            .isEqualTo(CommonErrorCode.NOT_FOUND)
    }

    @Test
    fun `수정 요청 days는 1 이상이어야 한다(검증)`() {
        val validator = Validation.buildDefaultValidatorFactory().validator
        val violations = validator.validate(CouponUpdateRequest(days = 0))
        assertThat(violations).isNotEmpty()
        assertThat(violations.map { it.propertyPath.toString() }).contains("days")
    }
}
