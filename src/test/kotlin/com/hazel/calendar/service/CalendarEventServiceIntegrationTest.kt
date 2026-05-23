package com.hazel.calendar.service

import com.hazel.auth.dto.SignupRequest
import com.hazel.auth.repository.UserRepository
import com.hazel.auth.service.AuthService
import com.hazel.calendar.dto.CalendarEventCreateRequest
import com.hazel.calendar.dto.CalendarEventUpdateRequest
import com.hazel.common.error.AppException
import com.hazel.common.tenant.TenantContext
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
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
class CalendarEventServiceIntegrationTest {
    @Autowired
    lateinit var service: CalendarEventService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): UUID {
        val email = "cal-${UUID.randomUUID()}@hazel.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun create(
        start: LocalDate,
        end: LocalDate,
    ) = service.create(CalendarEventCreateRequest("워크숍", start, end, null, null))

    @Test
    fun `생성·월 겹침 조회가 동작한다`() {
        newTenant()
        // 5월 28일~6월 3일 (5월·6월 모두 겹침)
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
        assertThat(service.update(e.id, CalendarEventUpdateRequest(title = "변경")).title).isEqualTo("변경")
        service.delete(e.id)
        assertThatThrownBy { service.get(e.id) }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `다른 테넌트의 이벤트는 조회할 수 없다`() {
        newTenant()
        val mine = create(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))
        newTenant()
        assertThatThrownBy { service.get(mine.id) }.isInstanceOf(AppException::class.java)
        assertThat(service.listByMonth("2026-06")).isEmpty()
    }
}
