package kr.ai.flori.expenses.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.expenses.dto.ExpenseCreateRequest
import kr.ai.flori.expenses.dto.ExpenseUpdateRequest
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
class ExpenseServiceIntegrationTest {
    @Autowired
    lateinit var expenseService: ExpenseService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "exp-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun req(
        unitPrice: Int = 5000,
        quantity: Int = 3,
        date: LocalDate = LocalDate.of(2026, 5, 10),
    ) = ExpenseCreateRequest(
        date = date,
        itemName = "장미 100송이",
        category = "flower_purchase",
        unitPrice = unitPrice,
        quantity = quantity,
        paymentMethod = "card",
        vendor = "양재 꽃시장",
    )

    @Test
    fun `생성 시 총액은 단가x수량으로 서버가 계산한다`() {
        newTenant()
        val expense = expenseService.create(req(unitPrice = 5000, quantity = 3))
        assertThat(expense.totalAmount).isEqualTo(15_000)
    }

    @Test
    fun `수정 시 총액이 재계산된다`() {
        newTenant()
        val created = expenseService.create(req())
        val updated = expenseService.update(created.id, ExpenseUpdateRequest(unitPrice = 10_000, quantity = 2))
        assertThat(updated.totalAmount).isEqualTo(20_000)
    }

    @Test
    fun `월 필터로 조회한다`() {
        newTenant()
        expenseService.create(req(date = LocalDate.of(2026, 5, 10)))
        expenseService.create(req(date = LocalDate.of(2026, 6, 10)))
        assertThat(expenseService.list("2026-05")).hasSize(1)
        assertThat(expenseService.list(null)).hasSize(2)
    }

    @Test
    fun `자동완성은 물품명·거래처·비고를 빈도순으로 반환한다`() {
        newTenant()
        repeat(2) { expenseService.create(req()) }
        val suggestions = expenseService.suggestions()
        assertThat(suggestions.itemNames).contains("장미 100송이")
        assertThat(suggestions.vendors).contains("양재 꽃시장")
    }

    @Test
    fun `다른 테넌트의 지출은 조회할 수 없다`() {
        newTenant()
        val mine = expenseService.create(req())
        newTenant()
        assertThatThrownBy { expenseService.get(mine.id) }.isInstanceOf(AppException::class.java)
        assertThat(expenseService.list(null)).isEmpty()
    }
}
