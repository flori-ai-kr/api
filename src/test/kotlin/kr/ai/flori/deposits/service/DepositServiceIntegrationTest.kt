package kr.ai.flori.deposits.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.service.SaleService
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
class DepositServiceIntegrationTest {
    @Autowired
    lateinit var depositService: DepositService

    @Autowired
    lateinit var saleService: SaleService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): UUID {
        val email = "dep-${UUID.randomUUID()}@flori.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun cardSale(amount: Int = 100_000) =
        saleService.create(
            SaleCreateRequest(LocalDate.of(2026, 5, 10), "basic_bouquet", amount, "card", cardCompany = "신한카드"),
        )

    @Test
    fun `입금 목록은 카드 매출만 포함하고 상태로 필터한다`() {
        newTenant()
        cardSale()
        cardSale()
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 10), "vase", 5_000, "cash"))

        assertThat(depositService.list(null, null, null)).hasSize(2)
        assertThat(depositService.list(null, "pending", null)).hasSize(2)
        assertThat(depositService.list(null, "completed", null)).isEmpty()
    }

    @Test
    fun `확인 시 completed + deposited_at, 되돌리기 시 pending + null`() {
        newTenant()
        val sale = cardSale()

        val confirmed = depositService.confirm(sale.id)
        assertThat(confirmed.depositStatus).isEqualTo("completed")
        assertThat(confirmed.depositedAt).isNotNull()

        val reverted = depositService.revert(sale.id)
        assertThat(reverted.depositStatus).isEqualTo("pending")
        assertThat(reverted.depositedAt).isNull()
    }

    @Test
    fun `다건 확인은 본인 매출을 일괄 완료한다`() {
        newTenant()
        val a = cardSale()
        val b = cardSale()
        assertThat(depositService.confirmMultiple(listOf(a.id, b.id))).isEqualTo(2)
        assertThat(depositService.list(null, "completed", null)).hasSize(2)
    }

    @Test
    fun `요약은 대기·완료 건수와 금액(예상입금액)을 집계한다`() {
        newTenant()
        val a = cardSale(100_000) // 수수료 2%, 예상입금 98000
        cardSale(50_000) //              예상입금 49000
        depositService.confirm(a.id)

        val summary = depositService.summary(null)
        assertThat(summary.completedCount).isEqualTo(1)
        assertThat(summary.completedAmount).isEqualTo(98_000)
        assertThat(summary.pendingCount).isEqualTo(1)
        assertThat(summary.pendingAmount).isEqualTo(49_000)
    }

    @Test
    fun `다른 테넌트의 입금은 확인할 수 없다`() {
        newTenant()
        val sale = cardSale()
        newTenant()
        assertThatThrownBy { depositService.confirm(sale.id) }.isInstanceOf(AppException::class.java)
        assertThat(depositService.confirmMultiple(listOf(sale.id))).isZero()
        assertThat(depositService.list(null, null, null)).isEmpty()
    }
}
