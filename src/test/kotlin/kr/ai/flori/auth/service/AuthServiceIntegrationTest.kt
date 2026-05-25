package kr.ai.flori.auth.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.LoginRequest
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AuthServiceIntegrationTest {
    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@flori.dev"

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
                "SELECT count(*) FROM $table WHERE user_id = ?::uuid",
                Long::class.java,
                userId,
            )

        assertThat(count("sale_categories")).isEqualTo(11)
        assertThat(count("payment_methods")).isEqualTo(4)
        assertThat(count("expense_categories")).isEqualTo(7)
        assertThat(count("expense_payment_methods")).isEqualTo(3)
        assertThat(count("card_company_settings")).isEqualTo(9)
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
}
