package kr.ai.flori.dashboard.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.expenses.dto.ExpenseCreateRequest
import kr.ai.flori.expenses.service.ExpenseService
import kr.ai.flori.reservations.dto.ReservationCreateRequest
import kr.ai.flori.reservations.service.ReservationService
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.service.SaleService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class DashboardServiceIntegrationTest {
    @Autowired
    lateinit var dashboardService: DashboardService

    @Autowired
    lateinit var saleService: SaleService

    @Autowired
    lateinit var expenseService: ExpenseService

    @Autowired
    lateinit var reservationService: ReservationService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    private val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "dash-${UUID.randomUUID()}@flori.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun seedData() {
        saleService.create(
            SaleCreateRequest(
                today,
                "basic_bouquet",
                100_000,
                "card",
                reservationChannel = "phone",
                customerPhone = "01010001000",
            ),
        )
        saleService.create(
            SaleCreateRequest(today, "vase", 50_000, "cash", reservationChannel = "road", customerPhone = "01010001000"),
        )
        saleService.create(SaleCreateRequest(today, "reservation", 30_000, "unpaid"))
        expenseService.create(ExpenseCreateRequest(today, "장미", "flower_purchase", 5_000, 2, "card"))
        reservationService.create(ReservationCreateRequest(today, null, "홍길동", null, "픽업"))
    }

    @Test
    fun `오늘 대시보드는 미수 제외 요약과 부속 데이터를 반환한다`() {
        newTenant()
        seedData()

        val result = dashboardService.today()
        assertThat(result.summary.totalAmount).isEqualTo(150_000) // 미수 30000 제외
        assertThat(result.summary.cardAmount).isEqualTo(100_000)
        assertThat(result.summary.cashAmount).isEqualTo(50_000)
        assertThat(result.recentSales).hasSize(3)
        assertThat(result.saleCategories).hasSize(11) // 가입 시드
        assertThat(result.upcomingReservations).isNotEmpty()
    }

    @Test
    fun `월 통계는 네이티브 집계로 카테고리·결제·채널·고객·지출을 산출한다`() {
        newTenant()
        seedData()

        val result = dashboardService.month(null)
        assertThat(result.summary.totalAmount).isEqualTo(150_000)
        assertThat(result.expenseTotal).isEqualTo(10_000) // 5000 * 2

        assertThat(result.categoryStats.first { it.name == "basic_bouquet" }.amount).isEqualTo(100_000)
        assertThat(result.paymentStats.first { it.method == "card" }.amount).isEqualTo(100_000)
        assertThat(result.paymentStats.first { it.method == "card" }.label).isEqualTo("카드")
        assertThat(result.channelStats.first { it.channel == "phone" }.amount).isEqualTo(100_000)
        assertThat(result.expenseStats.first { it.category == "flower_purchase" }.amount).isEqualTo(10_000)

        // 동일 전화번호 1명, 이전 매출 없음 → 신규 1
        assertThat(result.customerStats.totalCustomers).isEqualTo(1)
        assertThat(result.customerStats.newCustomers).isEqualTo(1)
    }

    @Test
    fun `카테고리 통계 비율 합은 100에 수렴한다`() {
        newTenant()
        seedData()
        val total = dashboardService.month(null).categoryStats.sumOf { it.percentage }
        assertThat(total).isBetween(99, 101)
    }

    @Test
    fun `다른 테넌트의 집계는 0이다`() {
        newTenant()
        seedData()
        newTenant() // 새 사용자
        val result = dashboardService.today()
        assertThat(result.summary.totalAmount).isZero()
        assertThat(result.recentSales).isEmpty()
    }
}
