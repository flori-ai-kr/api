package kr.ai.flori.statistics

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
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
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    private fun sale(
        date: LocalDate,
        category: String,
        amount: Int,
        payment: String?,
        isUnpaid: Boolean = false,
    ) = saleService.create(
        SaleCreateRequest(
            date,
            labelId(LabelKinds.CATEGORY, category),
            amount,
            payment?.let { labelId(LabelKinds.PAYMENT, it) },
            isUnpaid = isUnpaid,
        ),
    )

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

        // 분포: 카테고리별 금액(미수 제외) — basic_bouquet 30000, vase 20000
        val basicCat = labelId(LabelKinds.CATEGORY, "basic_bouquet")
        assertThat(result.categoryDistribution.first { it.id == basicCat }.amount).isEqualTo(30_000)
        assertThat(result.categoryDistribution.first { it.id == basicCat }.label).isEqualTo("기본 꽃다발")
        // 결제수단별 분포
        val cardPay = labelId(LabelKinds.PAYMENT, "card")
        assertThat(result.paymentDistribution.first { it.id == cardPay }.amount).isEqualTo(30_000)
    }

    @Test
    fun `증감(delta)은 직전 동일 길이 기간과 비교한다`() {
        newTenant()
        // 직전 기간(5/30~5/31): 카드 10000 1건
        sale(LocalDate.of(2026, 5, 30), "basic_bouquet", 10_000, "card")
        // 현재 기간(6/1~6/2): 카드 30000 + 현금 20000 = 50000, 2건
        sale(LocalDate.of(2026, 6, 1), "basic_bouquet", 30_000, "card")
        sale(LocalDate.of(2026, 6, 2), "vase", 20_000, "cash")

        val result = statisticsService.salesStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))

        assertThat(result.kpi.totalAmount).isEqualTo(50_000)
        // (50000 - 10000) / 10000 * 100 = 400
        assertThat(result.kpi.totalAmountDeltaPct).isEqualTo(400)
        // 2건 - 1건 = 1
        assertThat(result.kpi.countDelta).isEqualTo(1)
    }

    @Test
    fun `다른 테넌트의 매출은 집계에 포함되지 않는다`() {
        newTenant()
        sale(LocalDate.of(2026, 6, 1), "basic_bouquet", 30_000, "card")

        newTenant() // 새 사용자(데이터 없음)
        val result = statisticsService.salesStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))

        assertThat(result.kpi.totalAmount).isZero()
        assertThat(result.kpi.count).isZero()
        assertThat(result.timeseries).isEmpty()
        assertThat(result.categoryDistribution).isEmpty()
    }

    @Test
    fun `from이 to보다 뒤면 검증 에러(400)를 던진다`() {
        newTenant()
        assertThatThrownBy {
            statisticsService.salesStatistics(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1))
        }.isInstanceOf(AppException::class.java)
            .extracting { (it as AppException).errorCode }
            .isEqualTo(CommonErrorCode.VALIDATION)
    }
}
