package kr.ai.flori.settings

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.service.CardCompanyService
import kr.ai.flori.settings.service.PushSubscriptionService
import kr.ai.flori.settings.service.SaleCategorySettingService
import kr.ai.flori.settings.service.UserPreferenceService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SettingsServiceIntegrationTest {
    @Autowired
    lateinit var saleCategoryService: SaleCategorySettingService

    @Autowired
    lateinit var cardCompanyService: CardCompanyService

    @Autowired
    lateinit var userPreferenceService: UserPreferenceService

    @Autowired
    lateinit var pushSubscriptionService: PushSubscriptionService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): UUID {
        val email = "set-${UUID.randomUUID()}@flori.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    @Test
    fun `매출 카테고리는 가입 시드(11) + 추가·수정·삭제`() {
        newTenant()
        assertThat(saleCategoryService.list()).hasSize(11)

        val added = saleCategoryService.add("발렌타인 꽃다발", null, "valentine")
        assertThat(added.value).isEqualTo("valentine")
        assertThat(saleCategoryService.list()).hasSize(12)

        val updated = saleCategoryService.update(added.id, "발렌타인 특별", "#ffffff")
        assertThat(updated.label).isEqualTo("발렌타인 특별")

        saleCategoryService.delete(added.id)
        assertThat(saleCategoryService.list()).hasSize(11)
    }

    @Test
    fun `중복 value 카테고리는 409`() {
        newTenant()
        saleCategoryService.add("커스텀", null, "custom_x")
        assertThatThrownBy { saleCategoryService.add("다른이름", null, "custom_x") }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.DUPLICATE)
            }
    }

    @Test
    fun `카드사는 가입 시드(9) + 등록·수정·소프트삭제`() {
        newTenant()
        assertThat(cardCompanyService.list()).hasSize(9)

        val created = cardCompanyService.create("토스카드", BigDecimal("1.5"), 2)
        assertThat(cardCompanyService.list()).hasSize(10)

        val updated = cardCompanyService.update(created.id, BigDecimal("3.0"), 5)
        assertThat(updated.feeRate).isEqualByComparingTo(BigDecimal("3.0"))
        assertThat(updated.depositDays).isEqualTo(5)

        cardCompanyService.delete(created.id)
        assertThat(cardCompanyService.list()).hasSize(9) // 소프트 삭제 → 활성 목록에서 제외
    }

    @Test
    fun `중복 카드사는 409`() {
        newTenant()
        assertThatThrownBy { cardCompanyService.create("신한카드", BigDecimal("2.0"), 3) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `사용자 설정은 기본값 후 변경 저장`() {
        newTenant()
        assertThat(userPreferenceService.get().bottomNavItems).contains("sales")
        val items = listOf("dashboard", "sales", "expenses", "customers")
        assertThat(userPreferenceService.updateBottomNav(items).bottomNavItems).isEqualTo(items)
        assertThat(userPreferenceService.get().bottomNavItems).isEqualTo(items)
    }

    @Test
    fun `푸시 구독 등록·상태·해지`() {
        newTenant()
        assertThat(pushSubscriptionService.status().subscribed).isFalse()
        pushSubscriptionService.subscribe("fcm-token-${UUID.randomUUID()}", null, null, "test-agent")
        assertThat(pushSubscriptionService.status().subscribed).isTrue()
    }

    @Test
    fun `다른 테넌트의 설정은 격리된다`() {
        newTenant()
        saleCategoryService.add("내것", null, "mine_only")
        newTenant() // 다른 사용자
        assertThat(saleCategoryService.list().map { it.value }).doesNotContain("mine_only")
        assertThat(saleCategoryService.list()).hasSize(11) // 본인 시드만
    }
}
