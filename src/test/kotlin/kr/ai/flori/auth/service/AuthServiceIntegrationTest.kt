package kr.ai.flori.auth.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.RegisterCompleteRequest
import kr.ai.flori.auth.error.AuthErrorCode
import kr.ai.flori.auth.oauth.SocialOAuthClient
import kr.ai.flori.auth.oauth.SocialUserInfo
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.user.repository.UserRepository
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
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/**
 * 소셜 전용 인증 통합 검증.
 * - 신규 신원 → User 미생성 + registerToken/소셜 기본값 반환(registered=false).
 * - 기존 신원 → 로그인 토큰(registered=true).
 * - register/complete → User+프로필+기본 시드 생성 후 토큰, 재사용/이메일 충돌/만료 거부.
 *
 * 실제 제공자 호출을 막기 위해 SocialOAuthClient 스텁(KAKAO/GOOGLE/NAVER)으로 실 구현 빈을 오버라이드한다.
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
    lateinit var tokenProvider: JwtTokenProvider

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

    private fun completeRequest(
        registerToken: String,
        email: String,
        storeName: String = "헤이즐 플라워",
        phoneNumber: String = "01012345678",
        // 닉네임은 전역 유일(uq_users_nickname)이므로 기본값을 호출마다 고유하게 생성한다.
        nickname: String = "헤이즐-${UUID.randomUUID()}",
    ) = RegisterCompleteRequest(
        registerToken = registerToken,
        storeName = storeName,
        phoneNumber = phoneNumber,
        nickname = nickname,
        email = email,
        regionSido = "서울특별시",
    )

    @Test
    fun `신규 구글 로그인은 User를 만들지 않고 registerToken과 소셜 기본값을 반환한다`() {
        val pid = "google-${UUID.randomUUID()}"
        val email = uniqueEmail()
        googleStub.providerId = pid
        googleStub.email = email

        val result = authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)

        assertThat(result.registered).isFalse()
        assertThat(result.registerToken).isNotBlank()
        assertThat(result.token).isNull()
        assertThat(result.socialEmail).isEqualTo(email)
        assertThat(result.socialNickname).isEqualTo("GOOGLE 사장님")
        // User는 아직 생성되지 않는다(유령 계정 방지)
        assertThat(userRepository.findByProviderAndProviderId("GOOGLE", pid)).isNull()
    }

    @Test
    fun `register-complete는 User+프로필을 만들고 토큰을 발급한다`() {
        val pid = "google-${UUID.randomUUID()}"
        val email = uniqueEmail()
        googleStub.providerId = pid
        googleStub.email = email
        val registerToken =
            authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null).registerToken!!

        val tokens = authService.registerComplete(completeRequest(registerToken, email, nickname = "닉네임"))

        assertThat(tokens.accessToken).isNotBlank()
        assertThat(tokens.refreshToken).isNotBlank()
        val user = userRepository.findByProviderAndProviderId("GOOGLE", pid)!!
        assertThat(user.email).isEqualTo(email)
        assertThat(user.nickname).isEqualTo("닉네임")
    }

    @Test
    fun `register-complete 시 사용자별 기본 설정이 시드된다`() {
        val pid = "google-${UUID.randomUUID()}"
        val email = uniqueEmail()
        googleStub.providerId = pid
        googleStub.email = email
        val registerToken =
            authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null).registerToken!!
        authService.registerComplete(completeRequest(registerToken, email))
        val userId = userRepository.findByProviderAndProviderId("GOOGLE", pid)!!.id

        fun count(
            domain: String,
            kind: String,
        ) = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM label_settings WHERE user_id = ?::bigint AND domain = ? AND kind = ?",
            Long::class.java,
            userId,
            domain,
            kind,
        )

        assertThat(count("sale", "category")).isEqualTo(11)
        assertThat(count("sale", "payment")).isEqualTo(4)
        assertThat(count("sale", "channel")).isEqualTo(5)
        assertThat(count("expense", "category")).isEqualTo(7)
        assertThat(count("expense", "payment")).isEqualTo(3)
    }

    @Test
    fun `가입 완료된 신원으로 재로그인하면 registered=true 토큰을 받는다`() {
        val pid = "google-${UUID.randomUUID()}"
        val email = uniqueEmail()
        googleStub.providerId = pid
        googleStub.email = email
        val registerToken =
            authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null).registerToken!!
        authService.registerComplete(completeRequest(registerToken, email))

        val relogin = authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null)

        assertThat(relogin.registered).isTrue()
        assertThat(relogin.token).isNotNull
        assertThat(relogin.token!!.accessToken).isNotBlank()
        assertThat(relogin.registerToken).isNull()
    }

    @Test
    fun `이미 가입된 신원의 registerToken을 재사용하면 DUPLICATE`() {
        val pid = "google-${UUID.randomUUID()}"
        val email = uniqueEmail()
        googleStub.providerId = pid
        googleStub.email = email
        val registerToken =
            authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null).registerToken!!
        authService.registerComplete(completeRequest(registerToken, email))

        // 같은 (provider, providerId)로 발급한 또 다른 registerToken 재사용 시도
        val reused = tokenProvider.generateRegisterToken("GOOGLE", pid, email, "헤이즐")
        assertThatThrownBy { authService.registerComplete(completeRequest(reused, uniqueEmail())) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(AuthErrorCode.ALREADY_REGISTERED)
            }
    }

    @Test
    fun `다른 계정이 쓰는 이메일로 register-complete 하면 DUPLICATE`() {
        // 먼저 한 계정이 이메일을 선점
        val takenEmail = uniqueEmail()
        val firstToken = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", takenEmail, "선점")
        authService.registerComplete(completeRequest(firstToken, takenEmail))

        // 다른 신원이 같은 이메일을 주장 → 자동 병합 금지, DUPLICATE
        val pid = "google-${UUID.randomUUID()}"
        googleStub.providerId = pid
        googleStub.email = null
        val registerToken =
            authService.oauthLogin("GOOGLE", "code", "https://flori.kr/auth/callback/google", null).registerToken!!

        assertThatThrownBy { authService.registerComplete(completeRequest(registerToken, takenEmail)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(AuthErrorCode.DUPLICATE_EMAIL)
            }
    }

    @Test
    fun `다른 계정이 쓰는 닉네임으로 register-complete 하면 DUPLICATE`() {
        // 먼저 한 계정이 닉네임을 선점
        val takenNickname = "선점닉-${UUID.randomUUID()}"
        val firstEmail = uniqueEmail()
        val firstToken = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", firstEmail, "x")
        authService.registerComplete(completeRequest(firstToken, firstEmail, nickname = takenNickname))

        // 다른 신원이 같은 닉네임을 주장 → DUPLICATE
        val secondToken =
            tokenProvider.generateRegisterToken("GOOGLE", "google-${UUID.randomUUID()}", uniqueEmail(), "x")
        assertThatThrownBy {
            authService.registerComplete(completeRequest(secondToken, uniqueEmail(), nickname = takenNickname))
        }.isInstanceOfSatisfying(AppException::class.java) {
            assertThat(it.errorCode).isEqualTo(AuthErrorCode.DUPLICATE_NICKNAME)
            assertThat(it.message).contains("닉네임")
        }
    }

    @Test
    fun `위조-잘못된 registerToken으로 register-complete 하면 INVALID_TOKEN`() {
        assertThatThrownBy { authService.registerComplete(completeRequest("not.a.valid.token", uniqueEmail())) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.INVALID_TOKEN)
            }
    }

    @Test
    fun `네이버 신규 로그인도 registerToken을 발급하고 가입 완료로 User를 만든다`() {
        val pid = "naver-${UUID.randomUUID()}"
        val email = uniqueEmail()
        naverStub.providerId = pid
        naverStub.email = email
        val result = authService.oauthLogin("NAVER", "code", "https://flori.kr/auth/callback/naver", "state-1")
        assertThat(result.registered).isFalse()

        authService.registerComplete(completeRequest(result.registerToken!!, email))

        val user = userRepository.findByProviderAndProviderId("NAVER", pid)!!
        assertThat(user.provider).isEqualTo("NAVER")
    }

    @Test
    fun `dedup 윈도 내 중복 refresh는 회전을 1회만 하고 같은 토큰을 멱등 반환한다`() {
        val email = uniqueEmail()
        val registerToken = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", email, "닉")
        val first = authService.registerComplete(completeRequest(registerToken, email))

        val r1 = authService.refresh(first.refreshToken)
        val r2 = authService.refresh(first.refreshToken) // 같은 토큰 재호출(윈도 내)

        // 회전은 실제로 일어났고(원본과 다른 토큰), 중복 호출은 동일 결과를 받는다.
        assertThat(r1.refreshToken).isNotEqualTo(first.refreshToken)
        assertThat(r2.refreshToken).isEqualTo(r1.refreshToken)
        assertThat(r2.accessToken).isEqualTo(r1.accessToken)
    }

    @Test
    fun `같은 refresh 토큰으로 동시 다발 호출해도 전부 성공하고 같은 토큰을 받는다`() {
        val email = uniqueEmail()
        val registerToken = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", email, "닉")
        val first = authService.registerComplete(completeRequest(registerToken, email))

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val barrier = CyclicBarrier(threads)
        val refreshTokens = Collections.synchronizedList(mutableListOf<String>())
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        val futures =
            (1..threads).map {
                pool.submit {
                    try {
                        barrier.await() // 동시 발사
                        refreshTokens.add(authService.refresh(first.refreshToken).refreshToken)
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        futures.forEach { it.get() }
        pool.shutdown()

        // 멱등 처리로 아무도 INVALID_TOKEN을 받지 않고, 전원이 동일한 새 토큰을 받는다.
        assertThat(errors).isEmpty()
        assertThat(refreshTokens).hasSize(threads)
        assertThat(refreshTokens.toSet()).hasSize(1)
        assertThat(refreshTokens.first()).isNotEqualTo(first.refreshToken)
    }

    @Test
    fun `로그아웃은 멱등 캐시를 무효화해 윈도 내 옛 토큰 재사용을 막는다`() {
        val email = uniqueEmail()
        val registerToken = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", email, "닉")
        val first = authService.registerComplete(completeRequest(registerToken, email))

        // refresh로 회전 → first.refreshToken이 dedup 캐시에 키로 적재됨
        authService.refresh(first.refreshToken)
        // 같은(옛) 토큰으로 로그아웃 → DB 무효화 + 캐시 무효화
        authService.logout(first.refreshToken)

        // 윈도 내라도 캐시가 비워졌으므로 옛 토큰 재사용은 거부된다(캐시 히트 없음 → ROTATED 재회전 시도 → INVALID)
        assertThatThrownBy { authService.refresh(first.refreshToken) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.INVALID_TOKEN)
            }
    }

    @Test
    fun `로그아웃 후 refresh 토큰은 무효다`() {
        val email = uniqueEmail()
        val registerToken = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", email, "닉")
        val tokens = authService.registerComplete(completeRequest(registerToken, email))

        authService.logout(tokens.refreshToken)

        assertThatThrownBy { authService.refresh(tokens.refreshToken) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `me-email로 이메일을 변경할 수 있고 타 계정 이메일이면 DUPLICATE`() {
        val emailA = uniqueEmail()
        val tokenA = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", emailA, "A")
        authService.registerComplete(completeRequest(tokenA, emailA))
        val userIdA = userRepository.findByEmail(emailA)!!.id!!

        val newEmail = uniqueEmail()
        assertThat(authService.updateEmail(userIdA, newEmail).email).isEqualTo(newEmail)

        // 다른 사용자가 선점한 이메일로 변경 시도 → DUPLICATE
        val emailB = uniqueEmail()
        val tokenB = tokenProvider.generateRegisterToken("KAKAO", "kakao-${UUID.randomUUID()}", emailB, "B")
        authService.registerComplete(completeRequest(tokenB, emailB))

        assertThatThrownBy { authService.updateEmail(userIdA, emailB) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(AuthErrorCode.DUPLICATE_EMAIL)
            }
    }
}
