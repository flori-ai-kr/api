package kr.ai.flori.schedules.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.schedules.dto.ScheduleCreateRequest
import kr.ai.flori.schedules.dto.ScheduleUpdateRequest
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class ScheduleServiceIntegrationTest {
    @Autowired
    lateinit var service: ScheduleService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "sched-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun create(
        start: LocalDate,
        end: LocalDate,
    ) = service.create(ScheduleCreateRequest("워크숍", start, end, null, null))

    @Test
    fun `생성·월 겹침 조회가 동작한다`() {
        newTenant()
        create(LocalDate.of(2026, 5, 28), LocalDate.of(2026, 6, 3))
        assertThat(service.listByMonth("2026-05")).hasSize(1)
        assertThat(service.listByMonth("2026-06")).hasSize(1)
        assertThat(service.listByMonth("2026-07")).isEmpty()
    }

    @Test
    fun `종료일이 시작일보다 빠르면 거부된다`() {
        newTenant()
        assertThatThrownBy { create(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1)) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `수정·삭제가 동작한다`() {
        newTenant()
        val e = create(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))
        assertThat(service.update(e.id, ScheduleUpdateRequest(title = "변경")).title).isEqualTo("변경")
        service.delete(e.id)
        assertThatThrownBy { service.get(e.id) }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `다른 테넌트의 일정은 조회할 수 없다`() {
        newTenant()
        val mine = create(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))
        newTenant()
        assertThatThrownBy { service.get(mine.id) }.isInstanceOf(AppException::class.java)
        assertThat(service.listByMonth("2026-06")).isEmpty()
    }
}
