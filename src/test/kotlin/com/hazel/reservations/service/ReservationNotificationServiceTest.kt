package com.hazel.reservations.service

import com.hazel.auth.dto.SignupRequest
import com.hazel.auth.repository.UserRepository
import com.hazel.auth.service.AuthService
import com.hazel.common.tenant.TenantContext
import com.hazel.reservations.dto.ReservationCreateRequest
import com.hazel.reservations.dto.ReservationUpdateRequest
import com.hazel.reservations.repository.ReservationRepository
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class ReservationNotificationServiceTest {
    @Autowired
    lateinit var notificationService: ReservationNotificationService

    @Autowired
    lateinit var reservationService: ReservationService

    @Autowired
    lateinit var reservationRepository: ReservationRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): UUID {
        val email = "notif-${UUID.randomUUID()}@hazel.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    @Test
    fun `도달한 리마인더는 발송 후 reminder_sent로 마킹된다`() {
        newTenant()
        val r = reservationService.create(ReservationCreateRequest(LocalDate.of(2026, 6, 1), null, "홍길동", null, "픽업"))
        reservationService.update(r.id, ReservationUpdateRequest(reminderAt = Instant.now().minusSeconds(60)))

        notificationService.markAndNotifyDueReminders(Instant.now())

        assertThat(requireNotNull(reservationRepository.findById(r.id).orElse(null)).reminderSent).isTrue()
    }

    @Test
    fun `미도달 리마인더는 마킹되지 않는다`() {
        newTenant()
        val r = reservationService.create(ReservationCreateRequest(LocalDate.of(2026, 6, 1), null, "홍길동", null, "픽업"))
        reservationService.update(r.id, ReservationUpdateRequest(reminderAt = Instant.now().plusSeconds(3_600)))

        notificationService.markAndNotifyDueReminders(Instant.now())

        assertThat(requireNotNull(reservationRepository.findById(r.id).orElse(null)).reminderSent).isFalse()
    }

    @Test
    fun `일일 요약은 해당 날짜 예약이 있는 사용자를 집계한다`() {
        newTenant()
        val today = LocalDate.of(2026, 6, 15)
        reservationService.create(ReservationCreateRequest(today, null, "홍길동", null, "픽업A"))

        assertThat(notificationService.sendDailySummary(today)).isGreaterThanOrEqualTo(1)
    }
}
