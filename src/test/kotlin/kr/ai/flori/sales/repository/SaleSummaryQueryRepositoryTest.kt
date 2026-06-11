package kr.ai.flori.sales.repository

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
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
 * 매출 요약 집계 SQL 직접 검증. GET /sales 와 동일한 필터 규약(SaleSpecifications.filter)을
 * 따르는 동적 WHERE 빌딩과 결제수단 버킷(card/naverpay/transfer/cash) 매핑을 단독 테스트한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SaleSummaryQueryRepositoryTest {
    @Autowired
    lateinit var summaryRepository: SaleSummaryQueryRepository

    @Autowired
    lateinit var saleRepository: SaleRepository

    @Autowired
    lateinit var labelSettingRepository: LabelSettingRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun payId(
        userId: Long,
        value: String,
    ): Long = Fixtures.labelId(labelSettingRepository, userId, LabelDomains.SALE, LabelKinds.PAYMENT, value)

    private fun catId(
        userId: Long,
        value: String = "basic_bouquet",
    ): Long = Fixtures.labelId(labelSettingRepository, userId, LabelDomains.SALE, LabelKinds.CATEGORY, value)

    private fun summarize(
        userId: Long,
        month: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        categories: List<Long>? = null,
        payments: List<Long>? = null,
        channels: List<Long>? = null,
        search: String? = null,
    ) = summaryRepository.summarize(userId, month, startDate, endDate, categories, payments, channels, search)

    @Test
    fun `필터 없음 - total은 미수 제외 합계, 버킷은 결제수단 value 기준, cnt는 전체`() {
        val userId = newTenant()
        val cat = catId(userId)
        saleRepository.save(Fixtures.sale(userId, categoryId = cat, amount = 10_000, paymentMethodId = payId(userId, "card")))
        saleRepository.save(Fixtures.sale(userId, categoryId = cat, amount = 20_000, paymentMethodId = payId(userId, "cash")))
        saleRepository.save(Fixtures.sale(userId, categoryId = cat, amount = 30_000, paymentMethodId = null, isUnpaid = true))

        val summary = summarize(userId)

        assertThat(summary.total).isEqualTo(30_000) // 미수 제외(card+cash)
        assertThat(summary.card).isEqualTo(10_000)
        assertThat(summary.cash).isEqualTo(20_000)
        assertThat(summary.naverpay).isEqualTo(0)
        assertThat(summary.transfer).isEqualTo(0)
        assertThat(summary.count).isEqualTo(3) // 전체(미수 포함)
    }

    @Test
    fun `월·기간 필터 - 범위 밖 매출은 제외하고 기간이 월보다 우선한다`() {
        val userId = newTenant()
        val card = payId(userId, "card")
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 5, 10), amount = 10_000, paymentMethodId = card))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 6, 5), amount = 20_000, paymentMethodId = card))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 6, 25), amount = 40_000, paymentMethodId = card))

        assertThat(summarize(userId, month = "2026-06").total).isEqualTo(60_000)
        assertThat(summarize(userId, startDate = "2026-06-01", endDate = "2026-06-10").total).isEqualTo(20_000)
        // 기간 우선: month 와 함께 와도 기간이 적용된다
        assertThat(summarize(userId, month = "2026-05", startDate = "2026-06-01", endDate = "2026-06-30").total).isEqualTo(60_000)
    }

    @Test
    fun `카테고리·결제수단 IN 필터`() {
        val userId = newTenant()
        val basic = catId(userId, "basic_bouquet")
        val basket = catId(userId, "basket")
        val card = payId(userId, "card")
        val cash = payId(userId, "cash")
        saleRepository.save(Fixtures.sale(userId, categoryId = basic, amount = 10_000, paymentMethodId = card))
        saleRepository.save(Fixtures.sale(userId, categoryId = basket, amount = 20_000, paymentMethodId = cash))

        assertThat(summarize(userId, categories = listOf(basic)).total).isEqualTo(10_000)
        assertThat(summarize(userId, payments = listOf(cash)).total).isEqualTo(20_000)
        assertThat(summarize(userId, categories = listOf(basic, basket)).count).isEqualTo(2)
    }

    @Test
    fun `search - 고객명·메모 LIKE 검색, 와일드카드 문자는 리터럴로 이스케이프된다`() {
        val userId = newTenant()
        val card = payId(userId, "card")
        saleRepository.save(
            Fixtures.sale(userId, amount = 10_000, paymentMethodId = card, customerName = "김장미", memo = "진달래%특가"),
        )
        saleRepository.save(
            Fixtures.sale(userId, amount = 20_000, paymentMethodId = card, customerName = "박세일", memo = "진달래와특가"),
        )

        assertThat(summarize(userId, search = "장미").count).isEqualTo(1)
        // '%'가 이스케이프되지 않으면 "진달래와특가"도 매칭되어 2건이 된다
        assertThat(summarize(userId, search = "래%특").count).isEqualTo(1)
        assertThat(summarize(userId, search = "없는검색어").count).isEqualTo(0)
    }

    @Test
    fun `테넌트 격리 - 다른 사용자 매출은 집계되지 않는다`() {
        val other = newTenant()
        saleRepository.save(Fixtures.sale(other, amount = 99_000, paymentMethodId = payId(other, "card")))

        val userId = newTenant()
        saleRepository.save(Fixtures.sale(userId, amount = 10_000, paymentMethodId = payId(userId, "card")))

        val summary = summarize(userId)
        assertThat(summary.total).isEqualTo(10_000)
        assertThat(summary.count).isEqualTo(1)
    }
}
