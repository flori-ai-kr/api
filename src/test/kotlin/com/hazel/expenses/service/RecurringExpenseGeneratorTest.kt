package com.hazel.expenses.service

import com.hazel.auth.dto.SignupRequest
import com.hazel.auth.repository.UserRepository
import com.hazel.auth.service.AuthService
import com.hazel.common.tenant.TenantContext
import com.hazel.expenses.dto.RecurringExpenseRequest
import com.hazel.expenses.entity.RecurringSkip
import com.hazel.expenses.repository.RecurringSkipRepository
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Date
import java.time.LocalDate
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class RecurringExpenseGeneratorTest {
    @Autowired
    lateinit var generator: RecurringExpenseGenerator

    @Autowired
    lateinit var recurringService: RecurringExpenseService

    @Autowired
    lateinit var skipRepository: RecurringSkipRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): UUID {
        val email = "gen-${UUID.randomUUID()}@hazel.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun monthlyRule(day: Int) =
        recurringService.create(
            RecurringExpenseRequest(
                itemName = "월세",
                category = "rent",
                unitPrice = 500_000,
                quantity = 1,
                paymentMethod = "transfer",
                frequency = "monthly",
                daysOfMonth = listOf(day),
                startDate = LocalDate.of(2026, 1, 1),
            ),
        )

    private fun generatedCount(
        recurringId: UUID,
        date: LocalDate,
    ): Long =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM expenses WHERE recurring_id = ?::uuid AND date = ?",
            Long::class.java,
            recurringId,
            Date.valueOf(date),
        ) ?: 0

    @Test
    fun `발생일에 지출을 생성한다`() {
        newTenant()
        val rule = monthlyRule(15)
        val date = LocalDate.of(2026, 6, 15)

        generator.generateForDate(date)

        assertThat(generatedCount(rule.id, date)).isEqualTo(1)
    }

    @Test
    fun `중복 실행에도 한 건만 생성된다(멱등)`() {
        newTenant()
        val rule = monthlyRule(20)
        val date = LocalDate.of(2026, 6, 20)

        generator.generateForDate(date)
        generator.generateForDate(date)

        assertThat(generatedCount(rule.id, date)).isEqualTo(1)
    }

    @Test
    fun `skip이 있으면 해당 발생일은 생성하지 않는다`() {
        val userId = newTenant()
        val rule = monthlyRule(10)
        val date = LocalDate.of(2026, 6, 10)
        skipRepository.save(RecurringSkip(userId, rule.id, date))

        generator.generateForDate(date)

        assertThat(generatedCount(rule.id, date)).isZero()
    }

    @Test
    fun `발생일이 아니면 생성하지 않는다`() {
        newTenant()
        val rule = monthlyRule(15)
        val date = LocalDate.of(2026, 6, 14)

        generator.generateForDate(date)

        assertThat(generatedCount(rule.id, date)).isZero()
    }
}
