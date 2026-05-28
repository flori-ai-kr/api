package kr.ai.flori.reservations.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.reservations.dto.AddPickupRequest
import kr.ai.flori.reservations.dto.ReservationCreateRequest
import kr.ai.flori.reservations.dto.ReservationUpdateRequest
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.service.SaleService
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class ReservationServiceIntegrationTest {
    @Autowired
    lateinit var service: ReservationService

    @Autowired
    lateinit var saleService: SaleService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "resv-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun create(date: LocalDate = LocalDate.of(2026, 6, 1)) =
        service.create(
            ReservationCreateRequest(
                date = date,
                customerName = "홍길동",
                title = "생일 꽃다발",
            ),
        )

    @Test
    fun `예약 생성·월별 조회가 동작한다`() {
        newTenant()
        create(LocalDate.of(2026, 6, 1))
        create(LocalDate.of(2026, 7, 1))
        assertThat(service.listByMonth("2026-06")).hasSize(1)
    }

    @Test
    fun `reminder_at 변경 시 reminder_sent가 리셋된다`() {
        newTenant()
        val r = create()
        val updated = service.update(r.id, ReservationUpdateRequest(reminderAt = Instant.now()))
        assertThat(updated.reminderSent).isFalse()
        assertThat(updated.reminderAt).isNotNull()
    }

    @Test
    fun `픽업 완료 처리`() {
        newTenant()
        val r = create()
        assertThat(service.markPickupCompleted(r.id, true).pickupCompleted).isTrue()
    }

    @Test
    fun `예약을 매출로 전환하면 sale_id가 연결된다`() {
        newTenant()
        val r = create()
        val sale =
            service.convertToSale(
                r.id,
                SaleCreateRequest(LocalDate.of(2026, 6, 1), "basic_bouquet", 50_000, "cash"),
            )
        assertThat(service.get(r.id).saleId).isEqualTo(sale.id)
    }

    @Test
    fun `매출에 픽업을 추가하면 고객정보를 상속한다`() {
        newTenant()
        val sale =
            saleService.create(
                SaleCreateRequest(
                    date = LocalDate.of(2026, 6, 1),
                    productCategory = "basket",
                    amount = 30_000,
                    paymentMethod = "cash",
                    customerName = "김영희",
                    customerPhone = "01055556666",
                ),
            )
        val pickup = service.addPickupToSale(sale.id, AddPickupRequest(LocalDate.of(2026, 6, 5), null, "픽업"))
        assertThat(pickup.customerName).isEqualTo("김영희")
        assertThat(pickup.saleId).isEqualTo(sale.id)
        assertThat(service.forSale(sale.id)).hasSize(1)
    }

    @Test
    fun `발동된 리마인더는 48시간 윈도 내만 반환한다`() {
        newTenant()
        service.update(create().id, ReservationUpdateRequest(reminderAt = Instant.now().minusSeconds(3_600))) // 1시간 전
        service.update(create().id, ReservationUpdateRequest(reminderAt = Instant.now().minusSeconds(50 * 3_600L))) // 50시간 전
        assertThat(service.triggeredReminders()).hasSize(1)
    }

    @Test
    fun `다른 테넌트의 예약은 조회할 수 없다`() {
        newTenant()
        val mine = create()
        newTenant()
        assertThatThrownBy { service.get(mine.id) }.isInstanceOf(AppException::class.java)
        assertThat(service.listByMonth("2026-06")).isEmpty()
    }
}
