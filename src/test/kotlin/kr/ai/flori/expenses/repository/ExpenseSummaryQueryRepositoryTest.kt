package kr.ai.flori.expenses.repository

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
 * 지출 요약 집계 SQL 직접 검증 — 총액·건수와 카테고리별 합계(라벨 해석 포함),
 * GET /expenses 와 동일한 필터 규약의 동적 WHERE 빌딩을 단독 테스트한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class ExpenseSummaryQueryRepositoryTest {
    @Autowired
    lateinit var summaryRepository: ExpenseSummaryQueryRepository

    @Autowired
    lateinit var expenseRepository: ExpenseRepository

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

    private fun catId(
        userId: Long,
        value: String,
    ): Long = Fixtures.labelId(labelSettingRepository, userId, LabelDomains.EXPENSE, LabelKinds.CATEGORY, value)

    private fun summarize(
        userId: Long,
        month: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        categories: List<Long>? = null,
        payments: List<Long>? = null,
        search: String? = null,
    ) = summaryRepository.summarize(userId, month, startDate, endDate, categories, payments, search)

    @Test
    fun `총액·건수와 카테고리별 합계(금액 내림차순·라벨 해석)를 집계한다`() {
        val userId = newTenant()
        val flower = catId(userId, "flower_purchase")
        val delivery = catId(userId, "delivery")
        expenseRepository.save(Fixtures.expense(userId, categoryId = flower, unitPrice = 10_000, quantity = 2)) // 20,000
        expenseRepository.save(Fixtures.expense(userId, categoryId = flower, unitPrice = 5_000, quantity = 1)) // 5,000
        expenseRepository.save(Fixtures.expense(userId, categoryId = delivery, unitPrice = 3_000, quantity = 1)) // 3,000

        val summary = summarize(userId)

        assertThat(summary.total).isEqualTo(28_000)
        assertThat(summary.count).isEqualTo(3)
        assertThat(summary.byCategory.map { it.categoryLabel to it.amount })
            .containsExactly("꽃 사입" to 25_000L, "배송비" to 3_000L)
    }

    @Test
    fun `월·기간·카테고리·검색 필터가 목록과 동일한 규약으로 적용된다`() {
        val userId = newTenant()
        val flower = catId(userId, "flower_purchase")
        val delivery = catId(userId, "delivery")
        expenseRepository.save(
            Fixtures.expense(userId, date = LocalDate.of(2026, 5, 10), categoryId = flower, unitPrice = 10_000, vendor = "양재시장"),
        )
        expenseRepository.save(
            Fixtures.expense(userId, date = LocalDate.of(2026, 6, 10), categoryId = delivery, unitPrice = 20_000, memo = "퀵배송 50%할인"),
        )

        assertThat(summarize(userId, month = "2026-05").total).isEqualTo(10_000)
        assertThat(summarize(userId, startDate = "2026-06-01", endDate = "2026-06-30").total).isEqualTo(20_000)
        assertThat(summarize(userId, categories = listOf(flower)).count).isEqualTo(1)
        assertThat(summarize(userId, search = "양재").total).isEqualTo(10_000)
        // '%' 이스케이프: "50%할"은 리터럴 매칭 — 메모에 그대로 포함된 1건만
        assertThat(summarize(userId, search = "50%할").count).isEqualTo(1)
        assertThat(summarize(userId, search = "50_할").count).isEqualTo(0)
    }

    @Test
    fun `테넌트 격리 - 다른 사용자 지출은 집계되지 않는다`() {
        val other = newTenant()
        expenseRepository.save(Fixtures.expense(other, unitPrice = 99_000))

        val userId = newTenant()
        expenseRepository.save(Fixtures.expense(userId, unitPrice = 10_000))

        assertThat(summarize(userId).total).isEqualTo(10_000)
        assertThat(summarize(userId).count).isEqualTo(1)
    }
}
