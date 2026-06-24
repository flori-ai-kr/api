package kr.ai.flori.verification.listener

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.notification.solapi.SolapiNotifier
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.dto.BusinessVerificationSubmitRequest
import kr.ai.flori.verification.service.BusinessVerificationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["aws.cloudfront.domain=cdn.example.com"])
class BusinessVerificationSubmitNotifyTest {
    @Autowired private lateinit var service: BusinessVerificationService

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @MockitoBean private lateinit var solapiNotifier: SolapiNotifier

    @AfterEach
    fun tearDown() = TenantContext.clear()

    // 제네릭 T 경유로 null을 비-널 Kotlin 파라미터에 안전 주입(mockito-kotlin 미사용 패턴).
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun captureStore(captor: ArgumentCaptor<String>): String {
        captor.capture()
        return uninitialized()
    }

    @Test
    fun `제출하면 접수 알림톡이 상호와 함께 발송된다`() {
        val email = "submit-notify@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)

        service.submit(
            BusinessVerificationSubmitRequest(
                businessNumber = "1234567890",
                businessName = "플로리 꽃집",
                representativeName = "홍길동",
                businessLicenseUrl = "https://cdn.example.com/business-licenses/$userId/a.jpg",
            ),
        )

        val storeCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(solapiNotifier).sendBusinessSubmitted(anyLong(), anyString(), captureStore(storeCaptor))
        assertThat(storeCaptor.value).isEqualTo("플로리 꽃집")
    }
}
