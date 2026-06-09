package kr.ai.flori.statistics

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.service.SaleService
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
import kr.ai.flori.statistics.service.StatisticsService
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class StatisticsServiceSalesTest {
    @Autowired
    lateinit var statisticsService: StatisticsService

    @Autowired
    lateinit var saleService: SaleService

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
        val email = "stats-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun labelId(
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

    @Test
    fun `매출 통계는 미수 제외 KPI·일별 시계열을 산출한다`() {
        newTenant()
        // 2026-06-01 카드 매출 30000
        saleService.create(
            SaleCreateRequest(
                LocalDate.of(2026, 6, 1),
                labelId(LabelKinds.CATEGORY, "basic_bouquet"),
                30_000,
                labelId(LabelKinds.PAYMENT, "card"),
            ),
        )
        // 2026-06-02 현금 매출 20000
        saleService.create(
            SaleCreateRequest(
                LocalDate.of(2026, 6, 2),
                labelId(LabelKinds.CATEGORY, "vase"),
                20_000,
                labelId(LabelKinds.PAYMENT, "cash"),
            ),
        )
        // 2026-06-02 미수 50000 (payment_method_id NULL, is_unpaid)
        saleService.create(
            SaleCreateRequest(
                LocalDate.of(2026, 6, 2),
                labelId(LabelKinds.CATEGORY, "reservation"),
                50_000,
                null,
                isUnpaid = true,
            ),
        )

        val result = statisticsService.salesStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))

        assertThat(result.kpi.totalAmount).isEqualTo(50_000) // 미수 제외
        assertThat(result.kpi.count).isEqualTo(2)
        assertThat(result.kpi.avgOrderValue).isEqualTo(25_000)
        assertThat(result.kpi.unpaidBalance).isEqualTo(50_000)
        assertThat(result.kpi.unpaidCount).isEqualTo(1)

        assertThat(result.timeseries).hasSize(2)
        assertThat(result.timeseries.first { it.date == LocalDate.of(2026, 6, 1) }.amount).isEqualTo(30_000)
    }
}
