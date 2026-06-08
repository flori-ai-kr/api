package kr.ai.flori.customers.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerGradeCreateRequest
import kr.ai.flori.customers.dto.CustomerGradeUpdateRequest
import kr.ai.flori.customers.repository.CustomerGradeRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class CustomerGradeServiceTest {
    @Autowired
    lateinit var gradeService: CustomerGradeService

    @Autowired
    lateinit var gradeRepository: CustomerGradeRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "grade-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    @Test
    fun `list는 기본 등급 4종을 시드한다`() {
        newTenant()
        val grades = gradeService.list()
        assertThat(grades.map { it.name }).containsExactly("신규", "단골", "VIP", "블랙리스트")
        assertThat(grades.last().threshold).isNull() // 블랙리스트 수동전용
    }

    @Test
    fun `create는 이름·임계치를 저장하고 sortOrder를 끝(max+1)으로 부여한다`() {
        newTenant()
        gradeService.list() // 기본 4종(sortOrder 최대 4)
        val created = gradeService.create(CustomerGradeCreateRequest(name = "플래티넘", threshold = 20))
        assertThat(created.name).isEqualTo("플래티넘")
        assertThat(created.threshold).isEqualTo(20)
        assertThat(created.sortOrder).isEqualTo(5)
    }

    @Test
    fun `create 중복 이름은 CONFLICT`() {
        newTenant()
        gradeService.create(CustomerGradeCreateRequest(name = "골드", threshold = 3))
        assertThatThrownBy { gradeService.create(CustomerGradeCreateRequest(name = "골드", threshold = 7)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.CONFLICT)
            }
    }

    @Test
    fun `update는 이름·임계치를 변경하고 clearThreshold로 NULL 처리한다`() {
        newTenant()
        val created = gradeService.create(CustomerGradeCreateRequest(name = "실버", threshold = 2))

        val renamed = gradeService.update(created.id, CustomerGradeUpdateRequest(name = "실버플러스", threshold = 4))
        assertThat(renamed.name).isEqualTo("실버플러스")
        assertThat(renamed.threshold).isEqualTo(4)

        val cleared = gradeService.update(created.id, CustomerGradeUpdateRequest(clearThreshold = true))
        assertThat(cleared.threshold).isNull()
    }

    @Test
    fun `delete는 2개 이상일 때 삭제한다`() {
        newTenant()
        gradeService.list() // 기본 4종
        val extra = gradeService.create(CustomerGradeCreateRequest(name = "임시", threshold = 99))
        gradeService.delete(extra.id)
        assertThat(gradeService.list().map { it.name }).doesNotContain("임시")
    }

    @Test
    fun `delete는 마지막 1개일 때 VALIDATION`() {
        val userId = newTenant()
        // 기본 시드 없이 직접 1개만 만든다.
        val only = gradeService.create(CustomerGradeCreateRequest(name = "유일", threshold = 0))
        // 시드가 끼어들지 않았는지 확인: 만약 list()를 부르지 않았다면 1개만 존재.
        assertThat(gradeRepository.countByUserId(userId)).isEqualTo(1L)
        assertThatThrownBy { gradeService.delete(only.id) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.VALIDATION)
            }
    }
}
