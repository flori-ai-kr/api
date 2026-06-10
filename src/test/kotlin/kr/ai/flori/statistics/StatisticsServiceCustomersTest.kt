package kr.ai.flori.statistics

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerCreateRequest
import kr.ai.flori.customers.service.CustomerGradeService
import kr.ai.flori.customers.service.CustomerService
import kr.ai.flori.pinDefaultTimeZoneToUtc
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.TimeZone
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class StatisticsServiceCustomersTest {
    @Autowired
    lateinit var statisticsService: StatisticsService

    @Autowired
    lateinit var saleService: SaleService

    @Autowired
    lateinit var customerService: CustomerService

    @Autowired
    lateinit var gradeService: CustomerGradeService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var labelSettingRepository: LabelSettingRepository

    companion object {
        private lateinit var originalZone: TimeZone

        /**
         * 프로덕션 JVM 시간대 패리티: main()과 동일하게 JVM 기본 시간대를 UTC로 고정한다.
         * 클래스 로드 시점(Spring 컨텍스트/HikariCP/pgjdbc 초기화 이전)에 적용되어야 하므로
         * @BeforeAll이 아닌 companion init 블록에서 고정한다 — @SpringBootTest는 컨텍스트가
         * @BeforeAll보다 먼저 부팅될 수 있다. Date 변환·집계가 환산 없이 일자 그대로 들어가도록 한다.
         */
        init {
            originalZone = TimeZone.getDefault()
            pinDefaultTimeZoneToUtc()
        }

        @JvmStatic
        @BeforeAll
        fun pinZone() {
            // 안전망: 다른 테스트가 기본 시간대를 바꿔놨더라도 본 클래스 실행 동안 UTC 보장.
            pinDefaultTimeZoneToUtc()
        }

        @JvmStatic
        @AfterAll
        fun restoreZone() {
            TimeZone.setDefault(originalZone)
        }
    }

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "stats-cust-${UUID.randomUUID()}@flori.dev"
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
        amount: Int,
        customerName: String,
        customerPhone: String,
    ) = saleService.create(
        SaleCreateRequest(
            date = date,
            categoryId = labelId(LabelKinds.CATEGORY, "basic_bouquet"),
            amount = amount,
            paymentMethodId = labelId(LabelKinds.PAYMENT, "card"),
            customerName = customerName,
            customerPhone = customerPhone,
        ),
    )

    /** 이름 없이 전화번호만 있는 매출 — resolveCustomerId가 customer_id를 NULL로 둔다(전화 매칭은 통계 쿼리가 수행). */
    private fun phoneOnlySale(
        date: LocalDate,
        amount: Int,
        customerPhone: String,
    ) = saleService.create(
        SaleCreateRequest(
            date = date,
            categoryId = labelId(LabelKinds.CATEGORY, "basic_bouquet"),
            amount = amount,
            paymentMethodId = labelId(LabelKinds.PAYMENT, "card"),
            customerPhone = customerPhone,
        ),
    )

    /** 기본 등급 시드 후 해당 고객을 VIP로 수동 지정(잠금) — 등급 분포/TOP 검증용. */
    private fun assignVip(customerId: Long) {
        val vipId = requireNotNull(gradeService.list().first { it.name == "VIP" }.id)
        customerService.updateGrade(customerId, vipId)
    }

    @Test
    fun `고객 통계는 신규·재방문·등급·성별·TOP을 산출한다`() {
        newTenant()
        // 등급·성별 분포용 고객 등록 (등급은 커스텀 등급 모델 — VIP 수동 지정)
        customerService.create(CustomerCreateRequest(name = "신규고객", phone = "010-1111-1111", gender = "female"))
        val vip = customerService.create(CustomerCreateRequest(name = "재방문고객", phone = "010-2222-2222", gender = "male"))
        assignVip(requireNotNull(vip.id))

        // 재방문 고객: 기간 이전(5/20)에 선구매 + 기간 내(6/2) 구매
        sale(LocalDate.of(2026, 5, 20), 10_000, "재방문고객", "010-2222-2222")
        sale(LocalDate.of(2026, 6, 2), 50_000, "재방문고객", "010-2222-2222")
        // 신규 고객: 기간 내(6/1) 최초 구매
        sale(LocalDate.of(2026, 6, 1), 30_000, "신규고객", "010-1111-1111")

        val result = statisticsService.customerStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        // KPI: 기간 내 구매 distinct 2명, 신규 1, 재방문 1, 재방문율 50%
        assertThat(result.kpi.total).isEqualTo(2)
        assertThat(result.kpi.newCustomers).isEqualTo(1)
        assertThat(result.kpi.returningCustomers).isEqualTo(1)
        assertThat(result.kpi.returningRatePct).isEqualTo(50)

        // 증감(delta): 직전 동일 길이 기간(30일: 5/2~5/31)과 비교.
        // 직전 기간엔 재방문고객의 5/20 구매만 존재 → prev total 1, prev new 1(그 이전 선구매 없음), prev returning 0.
        // 따라서 newDelta = 현재 신규(1) - 직전 신규(1) = 0, returningDelta = 현재 재방문(1) - 직전 재방문(0) = 1.
        assertThat(result.kpi.newDelta).isEqualTo(0)
        assertThat(result.kpi.returningDelta).isEqualTo(1)

        // 시계열: 신규 고객의 최초 구매일(6/1)에만 newCustomers=1 (재방문 고객의 6/2 구매는 신규 아님)
        assertThat(result.timeseries.first { it.date == LocalDate.of(2026, 6, 1) }.newCustomers).isEqualTo(1)
        assertThat(result.timeseries.none { it.date == LocalDate.of(2026, 6, 2) && it.newCustomers > 0 }).isTrue()

        // 등급 분포: 신규 1(기본), VIP 1(수동 지정)
        assertThat(result.gradeDistribution.first { it.grade == "신규" }.count).isEqualTo(1)
        assertThat(result.gradeDistribution.first { it.grade == "VIP" }.count).isEqualTo(1)

        // 성별 분포: female 1, male 1
        assertThat(result.genderDistribution.first { it.gender == "female" }.count).isEqualTo(1)
        assertThat(result.genderDistribution.first { it.gender == "male" }.count).isEqualTo(1)

        // TOP 고객: 금액 내림차순 — 재방문고객(기간 내 50000) > 신규고객(30000)
        assertThat(result.topCustomers.map { it.name }).containsExactly("재방문고객", "신규고객")
        val top = result.topCustomers.first()
        assertThat(top.totalAmount).isEqualTo(50_000)
        assertThat(top.purchaseCount).isEqualTo(1)
        assertThat(top.grade).isEqualTo("VIP")
    }

    @Test
    fun `TOP 고객은 customer_id 매출과 전화번호-only 매출이 섞여도 한 행으로 합쳐진다`() {
        newTenant()
        // 등록 고객 → 이름+전화 매출은 customer_id로 연결된다.
        val mixed = customerService.create(CustomerCreateRequest(name = "혼합고객", phone = "010-3333-3333", gender = "female"))
        assignVip(requireNotNull(mixed.id))
        sale(LocalDate.of(2026, 6, 1), 40_000, "혼합고객", "010-3333-3333") // customer_id 연결
        // 이름 없이 전화만 있는 매출 → customer_id는 NULL이지만 전화번호로 같은 고객과 매칭되어야 한다.
        phoneOnlySale(LocalDate.of(2026, 6, 3), 25_000, "010-3333-3333")

        val result = statisticsService.customerStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        // 같은 논리 고객이 한 행으로 — 두 매출이 합산(40000+25000=65000, 2건).
        assertThat(result.topCustomers).hasSize(1)
        val top = result.topCustomers.first()
        assertThat(top.name).isEqualTo("혼합고객")
        assertThat(top.grade).isEqualTo("VIP")
        assertThat(top.purchaseCount).isEqualTo(2)
        assertThat(top.totalAmount).isEqualTo(65_000)
    }

    @Test
    fun `조회 기간이 상한을 초과하면 검증 에러(400)를 던진다`() {
        newTenant()
        val from = LocalDate.of(2024, 1, 1)
        assertThatThrownBy {
            statisticsService.customerStatistics(from, from.plusDays(732))
        }.isInstanceOf(AppException::class.java)
            .extracting { (it as AppException).errorCode }
            .isEqualTo(CommonErrorCode.VALIDATION)
    }

    @Test
    fun `다른 테넌트의 고객은 집계에 포함되지 않는다`() {
        newTenant()
        customerService.create(CustomerCreateRequest(name = "타테넌트", phone = "010-9999-9999", gender = "female"))
        sale(LocalDate.of(2026, 6, 1), 30_000, "타테넌트", "010-9999-9999")

        newTenant() // 새 사용자(데이터 없음)
        val result = statisticsService.customerStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(result.kpi.total).isZero()
        assertThat(result.kpi.newCustomers).isZero()
        assertThat(result.kpi.returningCustomers).isZero()
        assertThat(result.timeseries).isEmpty()
        assertThat(result.gradeDistribution).isEmpty()
        assertThat(result.genderDistribution).isEmpty()
        assertThat(result.topCustomers).isEmpty()
    }

    @Test
    fun `from이 to보다 뒤면 검증 에러(400)를 던진다`() {
        newTenant()
        assertThatThrownBy {
            statisticsService.customerStatistics(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1))
        }.isInstanceOf(AppException::class.java)
            .extracting { (it as AppException).errorCode }
            .isEqualTo(CommonErrorCode.VALIDATION)
    }
}
