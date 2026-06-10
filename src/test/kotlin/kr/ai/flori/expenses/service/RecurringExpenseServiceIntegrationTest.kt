package kr.ai.flori.expenses.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.expenses.dto.RecurringExpenseRequest
import kr.ai.flori.expenses.dto.RecurringInstanceUpdateRequest
import kr.ai.flori.expenses.repository.ExpenseRepository
import kr.ai.flori.expenses.repository.RecurringExpenseRepository
import kr.ai.flori.expenses.repository.RecurringSkipRepository
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
class RecurringExpenseServiceIntegrationTest {
    @Autowired
    lateinit var service: RecurringExpenseService

    @Autowired
    lateinit var generator: RecurringExpenseGenerator

    @Autowired
    lateinit var recurringRepository: RecurringExpenseRepository

    @Autowired
    lateinit var expenseRepository: ExpenseRepository

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
        val email = "rec-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun monthlyRule(day: Int = 15) =
        service.create(
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

    private fun generateInstance(
        ruleId: Long,
        date: LocalDate,
    ): Long {
        generator.generateForDate(date)
        return jdbcTemplate.queryForObject(
            "SELECT id FROM expenses WHERE recurring_id = ?::bigint AND date = ?",
            Long::class.java,
            ruleId,
            Date.valueOf(date),
        )!!
    }

    @Test
    fun `생성·조회·토글이 동작한다`() {
        newTenant()
        val rule = monthlyRule()
        assertThat(service.list()).hasSize(1)
        val toggled = service.toggleActive(rule.id, false)
        assertThat(toggled.isActive).isFalse()
    }

    @Test
    fun `이것만 수정은 인스턴스만 바꾸고 템플릿은 유지한다`() {
        newTenant()
        val rule = monthlyRule()
        val expenseId = generateInstance(rule.id, LocalDate.of(2026, 6, 15))

        service.updateInstanceOnly(expenseId, RecurringInstanceUpdateRequest(unitPrice = 600_000))

        val instance = requireNotNull(expenseRepository.findById(expenseId).orElse(null))
        assertThat(instance.unitPrice).isEqualTo(600_000)
        assertThat(instance.isRecurringModified).isTrue()
        assertThat(requireNotNull(recurringRepository.findById(rule.id).orElse(null)).unitPrice).isEqualTo(500_000)
    }

    @Test
    fun `이후 모두 수정은 템플릿과 인스턴스를 함께 바꾼다`() {
        newTenant()
        val rule = monthlyRule()
        val expenseId = generateInstance(rule.id, LocalDate.of(2026, 6, 15))

        service.updateRecurringFromInstance(expenseId, RecurringInstanceUpdateRequest(itemName = "관리비"))

        assertThat(requireNotNull(recurringRepository.findById(rule.id).orElse(null)).itemName).isEqualTo("관리비")
        assertThat(requireNotNull(expenseRepository.findById(expenseId).orElse(null)).itemName).isEqualTo("관리비")
    }

    @Test
    fun `이것만 삭제는 skip을 남기고 인스턴스를 지운다`() {
        newTenant()
        val rule = monthlyRule()
        val date = LocalDate.of(2026, 6, 15)
        val expenseId = generateInstance(rule.id, date)

        service.deleteInstanceOnly(expenseId)

        assertThat(expenseRepository.findById(expenseId)).isEmpty
        assertThat(skipRepository.existsByRecurringIdAndSkipDate(rule.id, date)).isTrue()
    }

    @Test
    fun `이후 모두 삭제는 템플릿 종료일을 당기고 인스턴스를 지운다`() {
        newTenant()
        val rule = monthlyRule()
        val date = LocalDate.of(2026, 6, 15)
        val expenseId = generateInstance(rule.id, date)

        service.deleteRecurringFromInstance(expenseId)

        assertThat(expenseRepository.findById(expenseId)).isEmpty
        assertThat(requireNotNull(recurringRepository.findById(rule.id).orElse(null)).endDate)
            .isEqualTo(date.minusDays(1))
    }

    @Test
    fun `템플릿 삭제 시 skip은 지워지고 자동생성 지출은 recurring_id만 NULL로 보존된다`() {
        newTenant()
        val rule = monthlyRule()
        val keepDate = LocalDate.of(2026, 6, 15)
        val skipDate = LocalDate.of(2026, 7, 15)
        val survivingExpenseId = generateInstance(rule.id, keepDate)
        val toSkipExpenseId = generateInstance(rule.id, skipDate)
        // "이것만 삭제"로 skip 기록 생성
        service.deleteInstanceOnly(toSkipExpenseId)
        assertThat(skipRepository.existsByRecurringIdAndSkipDate(rule.id, skipDate)).isTrue()

        service.delete(rule.id)

        // 템플릿 삭제 + skip 기록 제거(CASCADE 대체)
        assertThat(recurringRepository.findById(rule.id)).isEmpty
        assertThat(skipRepository.existsByRecurringIdAndSkipDate(rule.id, skipDate)).isFalse()
        // 자동생성 지출은 보존되고 recurring_id만 NULL
        val survivor = requireNotNull(expenseRepository.findById(survivingExpenseId).orElse(null))
        assertThat(survivor.recurringId).isNull()
    }

    @Test
    fun `다른 테넌트의 고정비는 조회할 수 없다`() {
        newTenant()
        val rule = monthlyRule()
        newTenant()
        assertThatThrownBy { service.get(rule.id) }.isInstanceOf(AppException::class.java)
        assertThat(service.list()).isEmpty()
    }
}
