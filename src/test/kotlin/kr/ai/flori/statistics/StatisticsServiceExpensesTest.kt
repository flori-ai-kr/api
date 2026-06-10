package kr.ai.flori.statistics

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.expenses.dto.ExpenseCreateRequest
import kr.ai.flori.expenses.service.ExpenseService
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.service.SaleService
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
import kr.ai.flori.statistics.service.StatisticsService
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
class StatisticsServiceExpensesTest {
    @Autowired
    lateinit var statisticsService: StatisticsService

    @Autowired
    lateinit var saleService: SaleService

    @Autowired
    lateinit var expenseService: ExpenseService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var labelSettingRepository: LabelSettingRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "stats-exp-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun saleLabelId(
        kind: String,
        value: String,
    ): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.SALE,
                kind,
                value,
            ),
        ).id!!

    private fun expenseCategoryId(value: String): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.EXPENSE,
                LabelKinds.CATEGORY,
                value,
            ),
        ).id!!

    private fun expensePaymentId(value: String = "card"): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.EXPENSE,
                LabelKinds.PAYMENT,
                value,
            ),
        ).id!!

    private fun sale(
        date: LocalDate,
        category: String,
        amount: Int,
        payment: String?,
        isUnpaid: Boolean = false,
    ) = saleService.create(
        SaleCreateRequest(
            date,
            saleLabelId(LabelKinds.CATEGORY, category),
            amount,
            payment?.let { saleLabelId(LabelKinds.PAYMENT, it) },
            isUnpaid = isUnpaid,
        ),
    )

    private fun expense(
        date: LocalDate,
        category: String,
        unitPrice: Int,
        quantity: Int = 1,
    ) = expenseService.create(
        ExpenseCreateRequest(
            date = date,
            itemName = "지출 $category",
            categoryId = expenseCategoryId(category),
            unitPrice = unitPrice,
            quantity = quantity,
            paymentMethodId = expensePaymentId(),
        ),
    )

    @Test
    fun `지출 통계는 KPI·일별 시계열·카테고리 분포를 산출한다`() {
        newTenant()
        expense(LocalDate.of(2026, 6, 1), "flower_purchase", 10_000)
        expense(LocalDate.of(2026, 6, 2), "flower_purchase", 5_000)
        sale(LocalDate.of(2026, 6, 1), "basic_bouquet", 30_000, "card")

        val result = statisticsService.expensesStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))

        assertThat(result.kpi.totalAmount).isEqualTo(15_000)
        assertThat(result.kpi.count).isEqualTo(2)
        // 15000 / 30000 = 50%
        assertThat(result.kpi.expenseRatioPct).isEqualTo(50)
        // 30000 - 15000 = 15000
        assertThat(result.kpi.netProfit).isEqualTo(15_000)

        val day1 = result.timeseries.first { it.date == LocalDate.of(2026, 6, 1) }
        assertThat(day1.expense).isEqualTo(10_000)
        assertThat(day1.netProfit).isEqualTo(20_000) // 30000 - 10000

        val day2 = result.timeseries.first { it.date == LocalDate.of(2026, 6, 2) }
        assertThat(day2.expense).isEqualTo(5_000)
        assertThat(day2.netProfit).isEqualTo(-5_000) // 0 - 5000

        val cat = expenseCategoryId("flower_purchase")
        assertThat(result.categoryDistribution.first { it.id == cat }.amount).isEqualTo(15_000)
    }

    @Test
    fun `다른 테넌트의 지출은 집계에 포함되지 않는다`() {
        newTenant()
        expense(LocalDate.of(2026, 6, 1), "flower_purchase", 10_000)

        newTenant() // 새 사용자(데이터 없음)
        val result = statisticsService.expensesStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))

        assertThat(result.kpi.totalAmount).isZero()
        assertThat(result.kpi.count).isZero()
        assertThat(result.kpi.netProfit).isZero()
        assertThat(result.timeseries).isEmpty()
        assertThat(result.categoryDistribution).isEmpty()
    }

    @Test
    fun `from이 to보다 뒤면 검증 에러(400)를 던진다`() {
        newTenant()
        assertThatThrownBy {
            statisticsService.expensesStatistics(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1))
        }.isInstanceOf(AppException::class.java)
            .extracting { (it as AppException).errorCode }
            .isEqualTo(CommonErrorCode.VALIDATION)
    }
}
