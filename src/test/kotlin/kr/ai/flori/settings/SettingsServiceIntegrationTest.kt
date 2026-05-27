package kr.ai.flori.settings

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.service.PushSubscriptionService
import kr.ai.flori.settings.service.SaleCategorySettingService
import kr.ai.flori.settings.service.UserPreferenceService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SettingsServiceIntegrationTest {
    @Autowired
    lateinit var saleCategoryService: SaleCategorySettingService

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

    private fun newTenant(): Long {
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
