package kr.ai.flori.auth.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.LoginRequest
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.oauth.SocialOAuthClient
import kr.ai.flori.auth.oauth.SocialUserInfo
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.util.UUID

/**
 * 소셜 로그인 통합 검증: 신규 생성·재로그인 멱등·이메일 충돌 시 null 유지.
 * 실제 제공자 호출을 막기 위해 SocialOAuthClient 스텁(KAKAO/GOOGLE/NAVER)으로 실 구현 빈을 오버라이드한다.
 * 각 스텁은 호출마다 고정 providerId를 반환하되, email은 외부에서 주입한 값을 사용한다(충돌 시나리오 제어).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@Import(AuthServiceIntegrationTest.StubSocialConfig::class)
@TestPropertySource(properties = ["spring.main.allow-bean-definition-overriding=true"])
class AuthServiceIntegrationTest {
    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    @Qualifier("GOOGLE")
    lateinit var googleStub: ConfigurableSocialStub

    @Autowired
    @Qualifier("NAVER")
    lateinit var naverStub: ConfigurableSocialStub

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@flori.dev"

    /** 테스트가 providerId·email을 제어할 수 있는 스텁. */
    class ConfigurableSocialStub(
        private val provider: String,
    ) : SocialOAuthClient {
        var providerId: String = "$provider-stub"
        var email: String? = null
        var nickname: String? = "$provider 사장님"

        override fun authenticate(
            code: String,
            redirectUri: String,
            state: String?,
        ): SocialUserInfo = SocialUserInfo(provider, providerId, email, nickname)
    }

    @TestConfiguration
    class StubSocialConfig {
        @Bean("KAKAO")
        fun kakaoStub() = ConfigurableSocialStub("KAKAO")

        @Bean("GOOGLE")
        fun googleStub() = ConfigurableSocialStub("GOOGLE")

        @Bean("NAVER")
        fun naverStub() = ConfigurableSocialStub("NAVER")
    }

    @Test
    fun `가입은 사용자를 만들고 비밀번호를 해시로 저장하며 토큰을 발급한다`() {
        val email = uniqueEmail()

        val tokens = authService.signup(SignupRequest(email, "password123", "사장님"))

        assertThat(tokens.accessToken).isNotBlank()
        assertThat(tokens.refreshToken).isNotBlank()
        val user = userRepository.findByEmail(email)
        assertThat(user).isNotNull
        assertThat(user!!.passwordHash).isNotEqualTo("password123")
        assertThat(user.passwordHash).startsWith("\$2")
    }

    @Test
    fun `가입 시 사용자별 기본 설정이 시드된다`() {
        val email = uniqueEmail()
        authService.signup(SignupRequest(email, "password123", null))
        val userId = userRepository.findByEmail(email)!!.id

        fun count(table: String) =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM $table WHERE user_id = ?::bigint",
                Long::class.java,
                userId,
            )

        assertThat(count("sale_categories")).isEqualTo(11)
        assertThat(count("payment_methods")).isEqualTo(4)
        assertThat(count("expense_categories")).isEqualTo(7)
        assertThat(count("expense_payment_methods")).isEqualTo(3)
    }

    @Test
    fun `중복 이메일 가입은 DUPLICATE 예외`() {
        val email = uniqueEmail()
        authService.signup(SignupRequest(email, "password123", null))

        assertThatThrownBy { authService.signup(SignupRequest(email, "password123", null)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.DUPLICATE)
            }
    }

    @Test
    fun `잘못된 비밀번호 로그인은 INVALID_CREDENTIALS 예외`() {
        val email = uniqueEmail()
        authService.signup(SignupRequest(email, "password123", null))

        assertThatThrownBy { authService.login(LoginRequest(email, "wrong-password")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
            }
    }

    @Test
    fun `refresh 회전 후 이전 refresh 토큰은 무효다`() {
        val email = uniqueEmail()
        val first = authService.signup(SignupRequest(email, "password123", null))

        val rotated = authService.refresh(first.refreshToken)
        assertThat(rotated.accessToken).isNotBlank()

        assertThatThrownBy { authService.refresh(first.refreshToken) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_TOKEN)
            }
    }

    @Test
    fun `로그아웃 후 refresh 토큰은 무효다`() {
        val email = uniqueEmail()
        val tokens = authService.signup(SignupRequest(email, "password123", null))

        authService.logout(tokens.refreshToken)

        assertThatThrownBy { authService.refresh(tokens.refreshToken) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `구글 신규 로그인은 사용자를 생성하고 제공자 이메일을 채운다`() {
        val pid = "google-${UUID.randomUUID()}"
        val email = uniqueEmail()
        googleStub.providerId = pid
        googleStub.email = email

        val tokens = authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)

        assertThat(tokens.accessToken).isNotBlank()
        val user = userRepository.findByProviderAndProviderId("GOOGLE", pid)
        assertThat(user).isNotNull
        assertThat(user!!.email).isEqualTo(email)
        assertThat(user.provider).isEqualTo("GOOGLE")
    }

    @Test
    fun `구글 동일 신원 재로그인은 새 사용자를 만들지 않는다`() {
        val pid = "google-${UUID.randomUUID()}"
        googleStub.providerId = pid
        googleStub.email = uniqueEmail()

        authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)
        val firstId = userRepository.findByProviderAndProviderId("GOOGLE", pid)!!.id

        authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)
        val secondId = userRepository.findByProviderAndProviderId("GOOGLE", pid)!!.id

        assertThat(secondId).isEqualTo(firstId)
    }

    @Test
    fun `네이버 신규 로그인은 사용자를 생성한다`() {
        val pid = "naver-${UUID.randomUUID()}"
        naverStub.providerId = pid
        naverStub.email = uniqueEmail()

        authService.oauthLogin("NAVER", "code", "https://flori.kr/auth/callback/naver", "state-1")

        val user = userRepository.findByProviderAndProviderId("NAVER", pid)
        assertThat(user).isNotNull
        assertThat(user!!.provider).isEqualTo("NAVER")
    }

    @Test
    fun `네이버 동일 신원 재로그인은 멱등하다`() {
        val pid = "naver-${UUID.randomUUID()}"
        naverStub.providerId = pid
        naverStub.email = uniqueEmail()

        authService.oauthLogin("NAVER", "code", "https://flori.kr/auth/callback/naver", "state-1")
        val firstId = userRepository.findByProviderAndProviderId("NAVER", pid)!!.id

        authService.oauthLogin("NAVER", "code", "https://flori.kr/auth/callback/naver", "state-1")
        val secondId = userRepository.findByProviderAndProviderId("NAVER", pid)!!.id

        assertThat(secondId).isEqualTo(firstId)
    }

    @Test
    fun `소셜 신규 가입 시 제공자 이메일이 이미 사용 중이면 email을 null로 유지한다`() {
        // 기존 로컬 가입자가 이메일을 선점
        val takenEmail = uniqueEmail()
        authService.signup(SignupRequest(takenEmail, "password123", null))

        // 같은 이메일을 주장하는 구글 신규 로그인 → 자동 병합 금지, email null
        val pid = "google-${UUID.randomUUID()}"
        googleStub.providerId = pid
        googleStub.email = takenEmail

        authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)

        val social = userRepository.findByProviderAndProviderId("GOOGLE", pid)
        assertThat(social).isNotNull
        assertThat(social!!.email).isNull()
        // 선점한 로컬 계정은 그대로 유지
        assertThat(userRepository.findByEmail(takenEmail)!!.provider).isEqualTo("LOCAL")
    }

    @Test
    fun `소셜 가입 후 me-email로 이메일을 보완할 수 있다`() {
        val pid = "google-${UUID.randomUUID()}"
        googleStub.providerId = pid
        googleStub.email = null // 이메일 미제공 소셜 가입
        authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)
        val userId = userRepository.findByProviderAndProviderId("GOOGLE", pid)!!.id!!

        val newEmail = uniqueEmail()
        val res = authService.updateEmail(userId, newEmail)

        assertThat(res.email).isEqualTo(newEmail)
        assertThat(userRepository.findById(userId).get().email).isEqualTo(newEmail)
    }

    @Test
    fun `me-email 보완 시 다른 사용자가 쓰는 이메일이면 DUPLICATE`() {
        val takenEmail = uniqueEmail()
        authService.signup(SignupRequest(takenEmail, "password123", null))

        val pid = "google-${UUID.randomUUID()}"
        googleStub.providerId = pid
        googleStub.email = null
        authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)
        val userId = userRepository.findByProviderAndProviderId("GOOGLE", pid)!!.id!!

        assertThatThrownBy { authService.updateEmail(userId, takenEmail) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.DUPLICATE)
            }
    }
}
