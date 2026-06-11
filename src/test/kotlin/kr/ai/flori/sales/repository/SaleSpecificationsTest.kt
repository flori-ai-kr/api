package kr.ai.flori.sales.repository

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.sales.entity.Sale
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
 * 매출 목록 동적 필터(Specifications) 직접 검증.
 * GET /sales 목록과 /sales/summary 는 동일 필터 규약을 따라야 한다 — 마지막 케이스가 둘의 정합을 교차 검증한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SaleSpecificationsTest {
    @Autowired
    lateinit var saleRepository: SaleRepository

    @Autowired
    lateinit var summaryRepository: SaleSummaryQueryRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    @Suppress("LongParameterList")
    private fun find(
        userId: Long,
        month: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        categories: List<Long>? = null,
        payments: List<Long>? = null,
        channels: List<Long>? = null,
        search: String? = null,
    ): List<Sale> =
        saleRepository.findAll(
            SaleSpecifications.filter(userId, month, startDate, endDate, categories, payments, channels, search),
        )

    @Test
    fun `월 필터 - 연·월·일 형식을 지원하고 범위 밖은 제외한다`() {
        val userId = newTenant()
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 5, 10), amount = 1))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 6, 5), amount = 2))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2025, 12, 31), amount = 3))

        assertThat(find(userId, month = "2026-06")).hasSize(1)
        assertThat(find(userId, month = "2026")).hasSize(2)
        assertThat(find(userId, month = "2026-05-10")).hasSize(1)
        assertThat(find(userId)).hasSize(3)
    }

    @Test
    fun `기간 필터가 월 필터보다 우선한다`() {
        val userId = newTenant()
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 5, 10)))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 6, 5)))

        val result = find(userId, month = "2026-05", startDate = "2026-06-01", endDate = "2026-06-30")
        assertThat(result).hasSize(1)
        assertThat(result.first().date).isEqualTo(LocalDate.of(2026, 6, 5))
    }

    @Test
    fun `카테고리·결제수단·채널 IN 필터`() {
        val userId = newTenant()
        saleRepository.save(Fixtures.sale(userId, categoryId = 11L, paymentMethodId = 21L).apply { channelId = 31L })
        saleRepository.save(Fixtures.sale(userId, categoryId = 12L, paymentMethodId = 22L).apply { channelId = 32L })

        assertThat(find(userId, categories = listOf(11L))).hasSize(1)
        assertThat(find(userId, payments = listOf(22L))).hasSize(1)
        assertThat(find(userId, channels = listOf(31L, 32L))).hasSize(2)
        assertThat(find(userId, categories = listOf(99L))).isEmpty()
    }

    @Test
    fun `검색 - 고객명·메모 LIKE, 와일드카드는 리터럴 이스케이프`() {
        val userId = newTenant()
        saleRepository.save(Fixtures.sale(userId, customerName = "김장미", memo = "진달래%특가"))
        saleRepository.save(Fixtures.sale(userId, customerName = "박세일", memo = "진달래와특가"))

        assertThat(find(userId, search = "장미")).hasSize(1)
        assertThat(find(userId, search = "래%특")).hasSize(1) // '%' 미이스케이프면 2건
        assertThat(find(userId, search = "없는검색어")).isEmpty()
    }

    @Test
    fun `테넌트 격리 - 다른 사용자 매출은 어떤 필터로도 보이지 않는다`() {
        val other = newTenant()
        saleRepository.save(Fixtures.sale(other, customerName = "남의고객"))

        val userId = newTenant()
        saleRepository.save(Fixtures.sale(userId, customerName = "내고객"))

        assertThat(find(userId)).hasSize(1)
        assertThat(find(userId, search = "남의고객")).isEmpty()
    }

    @Test
    fun `목록과 summary는 동일 필터 규약 - 같은 조건의 건수가 일치한다`() {
        val userId = newTenant()
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 6, 1), categoryId = 11L, memo = "특가"))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 6, 2), categoryId = 11L))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 5, 1), categoryId = 12L))

        val listed = find(userId, month = "2026-06", categories = listOf(11L), search = "특가")
        val summary =
            summaryRepository.summarize(
                userId,
                month = "2026-06",
                startDate = null,
                endDate = null,
                categories = listOf(11L),
                payments = null,
                channels = null,
                search = "특가",
            )
        assertThat(summary.count).isEqualTo(listed.size.toLong())
        assertThat(listed).hasSize(1)
    }
}
