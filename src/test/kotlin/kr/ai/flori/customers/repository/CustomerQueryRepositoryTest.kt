package kr.ai.flori.customers.repository

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerStats
import kr.ai.flori.sales.repository.SaleRepository
import kr.ai.flori.support.Fixtures
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

/**
 * 고객 집계 SQL 직접 검증. CustomerService에서 분리된 네이티브 쿼리의 단독 테스트.
 * 데이터는 리포지토리로 직접 적재(FK 미사용 스키마) — 라벨 해석이 없는 집계라 라벨 id는 임의값.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class CustomerQueryRepositoryTest {
    @Autowired
    lateinit var queryRepository: CustomerQueryRepository

    @Autowired
    lateinit var saleRepository: SaleRepository

    @Autowired
    lateinit var customerRepository: CustomerRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun customerId(userId: Long): Long = requireNotNull(customerRepository.save(Fixtures.customer(userId)).id)

    private fun insertPhotoCard(
        userId: Long,
        customerId: Long?,
        url: String?,
        secondsOffset: Int,
    ): Long =
        requireNotNull(
            jdbcTemplate.queryForObject(
                """
                INSERT INTO photo_cards (user_id, title, photos, customer_id, created_at)
                VALUES (?::bigint, 'card', ?::jsonb, ?::bigint, NOW() + (? || ' seconds')::interval)
                RETURNING id
                """.trimIndent(),
                Long::class.java,
                userId,
                if (url != null) """[{"url":"$url","originalName":"o.jpg"}]""" else "[]",
                customerId,
                secondsOffset,
            ),
        )

    @Test
    fun `statsFor - 해당 고객의 건수·총액·최초·최근 구매일을 집계한다`() {
        val userId = newTenant()
        val cid = customerId(userId)
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 5, 1), amount = 10_000, customerId = cid))
        saleRepository.save(Fixtures.sale(userId, date = LocalDate.of(2026, 6, 3), amount = 25_000, customerId = cid))

        val stats = queryRepository.statsFor(userId, cid)

        assertThat(stats).isEqualTo(CustomerStats(2, 35_000, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 3)))
    }

    @Test
    fun `statsFor - 매출 없는 고객은 EMPTY`() {
        val userId = newTenant()
        val cid = customerId(userId)

        assertThat(queryRepository.statsFor(userId, cid)).isEqualTo(CustomerStats.EMPTY)
    }

    @Test
    fun `aggregateStats - 고객별 맵으로 집계하고 미연결 매출은 제외한다`() {
        val userId = newTenant()
        val c1 = customerId(userId)
        val c2 = customerId(userId)
        saleRepository.save(Fixtures.sale(userId, amount = 10_000, customerId = c1))
        saleRepository.save(Fixtures.sale(userId, amount = 20_000, customerId = c1))
        saleRepository.save(Fixtures.sale(userId, amount = 5_000, customerId = c2))
        saleRepository.save(Fixtures.sale(userId, amount = 99_000, customerId = null)) // 미연결 — 집계 제외

        val stats = queryRepository.aggregateStats(userId)

        assertThat(stats.keys).containsExactlyInAnyOrder(c1, c2)
        assertThat(stats[c1]!!.count).isEqualTo(2)
        assertThat(stats[c1]!!.total).isEqualTo(30_000)
        assertThat(stats[c2]!!.total).isEqualTo(5_000)
    }

    @Test
    fun `purchaseCounts - 고객별 구매 건수 맵`() {
        val userId = newTenant()
        val cid = customerId(userId)
        repeat(3) { saleRepository.save(Fixtures.sale(userId, customerId = cid)) }

        assertThat(queryRepository.purchaseCounts(userId)).isEqualTo(mapOf(cid to 3))
    }

    @Test
    fun `photoSummary - 최신순 대표 썸네일 최대 6장과 카운트, 사진 없는 카드는 카운트만`() {
        val userId = newTenant()
        val cid = customerId(userId)
        // 7장 + 사진 없는 카드 1장 → 카운트 8, 썸네일은 최신 6장
        val cardIds = (0..6).map { insertPhotoCard(userId, cid, "https://cdn/p$it.jpg", it) }
        insertPhotoCard(userId, cid, url = null, secondsOffset = 100)

        val (thumbs, count) = queryRepository.photoSummaryFor(userId, cid)

        assertThat(count).isEqualTo(8)
        assertThat(thumbs).hasSize(6)
        assertThat(thumbs.first().url).isEqualTo("https://cdn/p6.jpg")
        assertThat(thumbs.first().cardId).isEqualTo(cardIds[6])

        val byCustomer = queryRepository.photoSummaryByCustomer(userId)
        assertThat(byCustomer[cid]!!.second).isEqualTo(8)
        assertThat(byCustomer[cid]!!.first).isEqualTo(thumbs)
    }

    @Test
    fun `테넌트 격리 - 다른 사용자의 매출·사진은 집계에 섞이지 않는다`() {
        val other = newTenant()
        val otherCustomer = customerId(other)
        saleRepository.save(Fixtures.sale(other, amount = 77_000, customerId = otherCustomer))
        insertPhotoCard(other, otherCustomer, "https://cdn/other.jpg", 0)

        val userId = newTenant()
        val cid = customerId(userId)
        saleRepository.save(Fixtures.sale(userId, amount = 10_000, customerId = cid))

        assertThat(queryRepository.statsFor(userId, cid).total).isEqualTo(10_000)
        assertThat(queryRepository.aggregateStats(userId).keys).containsExactly(cid)
        assertThat(queryRepository.purchaseCounts(userId)).isEqualTo(mapOf(cid to 1))
        assertThat(queryRepository.photoSummaryByCustomer(userId)).isEmpty()
        assertThat(queryRepository.photoSummaryFor(userId, otherCustomer).second).isEqualTo(0)
    }
}
