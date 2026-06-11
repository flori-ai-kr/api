package kr.ai.flori.expenses.repository

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.expenses.entity.Expense
import kr.ai.flori.support.Fixtures
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * 지출 목록 동적 필터(Specifications) 직접 검증 — summary(JDBC)와 동일 필터 규약 정합 포함.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class ExpenseSpecificationsTest {
    @Autowired
    lateinit var expenseRepository: ExpenseRepository

    @Autowired
    lateinit var summaryRepository: ExpenseSummaryQueryRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun find(
        userId: Long,
        month: String? = null,
        categories: List<Long>? = null,
        search: String? = null,
    ): List<Expense> =
        expenseRepository.findAll(
            ExpenseSpecifications.filter(userId, month, null, null, categories, null, search),
        )

    @Test
    fun `검색 - 물품명·거래처·메모 LIKE, 와일드카드는 리터럴 이스케이프`() {
        val userId = newTenant()
        expenseRepository.save(Fixtures.expense(userId, itemName = "장미 단", memo = "50%할인"))
        expenseRepository.save(Fixtures.expense(userId, itemName = "수국", vendor = "50과할인마트"))

        assertThat(find(userId, search = "장미")).hasSize(1)
        assertThat(find(userId, search = "50%할")).hasSize(1) // '%' 미이스케이프면 2건
        assertThat(find(userId, search = "없는검색어")).isEmpty()
    }

    @Test
    fun `테넌트 격리와 summary 필터 정합 - 같은 조건의 건수가 일치한다`() {
        val other = newTenant()
        expenseRepository.save(Fixtures.expense(other, memo = "특가"))

        val userId = newTenant()
        expenseRepository.save(Fixtures.expense(userId, date = LocalDate.of(2026, 6, 1), categoryId = 11L, memo = "특가"))
        expenseRepository.save(Fixtures.expense(userId, date = LocalDate.of(2026, 5, 1), categoryId = 11L, memo = "특가"))

        val listed = find(userId, month = "2026-06", categories = listOf(11L), search = "특가")
        val summary = summaryRepository.summarize(userId, "2026-06", null, null, listOf(11L), null, "특가")
        assertThat(listed).hasSize(1)
        assertThat(summary.count).isEqualTo(listed.size.toLong())
    }
}
