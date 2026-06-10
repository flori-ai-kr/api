package kr.ai.flori.expenses.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.expenses.dto.RecurringExpenseRequest
import kr.ai.flori.expenses.entity.RecurringSkip
import kr.ai.flori.expenses.repository.RecurringSkipRepository
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
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
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var labelSettingRepository: LabelSettingRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    /** 시드된 지출 카테고리 value → label_settings id. */
    private fun catId(value: String): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.EXPENSE,
                LabelKinds.CATEGORY,
                value,
            ),
        ).id!!

    /** 시드된 지출 결제수단 value → label_settings id. */
    private fun payId(value: String): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.EXPENSE,
                LabelKinds.PAYMENT,
                value,
            ),
        ).id!!

    private fun newTenant(): Long {
        val email = "gen-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun monthlyRule(day: Int) =
        recurringService.create(
            RecurringExpenseRequest(
                itemName = "월세",
                categoryId = catId("rent"),
                unitPrice = 500_000,
                quantity = 1,
                paymentMethodId = payId("transfer"),
                frequency = "monthly",
                daysOfMonth = listOf(day),
                startDate = LocalDate.of(2026, 1, 1),
            ),
        )

    private fun generatedCount(
        recurringId: Long,
        date: LocalDate,
    ): Long =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM expenses WHERE recurring_id = ?::bigint AND date = ?",
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

    @Test
    fun `유효기간(endDate) 종료 후에는 발생일이어도 생성하지 않는다`() {
        newTenant()
        val rule =
            recurringService.create(
                RecurringExpenseRequest(
                    itemName = "월세",
                    categoryId = catId("rent"),
                    unitPrice = 500_000,
                    quantity = 1,
                    paymentMethodId = payId("transfer"),
                    frequency = "monthly",
                    daysOfMonth = listOf(15),
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = LocalDate.of(2026, 3, 31),
                ),
            )
        val date = LocalDate.of(2026, 6, 15) // 매월 15일이지만 유효기간 종료 이후

        generator.generateForDate(date)

        assertThat(generatedCount(rule.id, date)).isZero()
    }
}
