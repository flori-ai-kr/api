package kr.ai.flori.sales.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerCreateRequest
import kr.ai.flori.customers.repository.CustomerGradeRepository
import kr.ai.flori.customers.service.CustomerService
import kr.ai.flori.sales.dto.SaleCreateRequest
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
import java.time.LocalDate
import java.util.UUID

/**
 * SaleService 의 매출 변경 → 고객 등급 자동 재계산 훅을 end-to-end 로 검증한다.
 * recomputeGrade 를 직접 호출하지 않고, create/delete 만으로 등급이 따라 변하는지 본다.
 * 기본 시드 등급: 신규(0) / 단골(5) / VIP(10) / 블랙리스트(수동).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SaleGradePromotionTest {
    @Autowired
    lateinit var customerService: CustomerService

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

    @Autowired
    lateinit var gradeRepository: CustomerGradeRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "salegrade-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun catId(): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.SALE,
                LabelKinds.CATEGORY,
                "basic_bouquet",
            ),
        ).id!!

    private fun payId(): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.SALE,
                LabelKinds.PAYMENT,
                "cash",
            ),
        ).id!!

    private fun create(phone: String = "0102222${(1000..9999).random()}") =
        customerService.create(CustomerCreateRequest(name = "김매출", phone = phone))

    /** 고객에게 매출을 1건 등록하고 sale id 를 반환. */
    private fun addSale(customerId: Long): Long =
        requireNotNull(
            saleService
                .create(
                    SaleCreateRequest(
                        date = LocalDate.of(2026, 5, 1),
                        categoryId = catId(),
                        amount = 10_000,
                        paymentMethodId = payId(),
                        customerId = customerId,
                    ),
                ).id,
        )

    private fun gradeName(customerId: Long): String? = customerService.get(customerId).grade

    @Test
    fun `매출 5건 등록 시 단골로 자동 승급된다`() {
        newTenant()
        val c = create()
        assertThat(gradeName(c.id)).isEqualTo("신규")

        repeat(5) { addSale(c.id) }

        assertThat(gradeName(c.id)).isEqualTo("단골")
    }

    @Test
    fun `매출 10건 등록 시 VIP로 자동 승급된다`() {
        newTenant()
        val c = create()

        repeat(10) { addSale(c.id) }

        assertThat(gradeName(c.id)).isEqualTo("VIP")
    }

    @Test
    fun `등급 잠금 고객은 매출이 많아도 자동 재계산으로 바뀌지 않는다`() {
        val userId = newTenant()
        val c = create()
        val newGradeId = requireNotNull(gradeRepository.findByUserIdOrderBySortOrderAsc(userId).first { it.name == "신규" }.id)

        customerService.updateGrade(c.id, newGradeId) // 수동 고정 → 잠금

        repeat(12) { addSale(c.id) }

        val after = customerService.get(c.id)
        assertThat(after.grade).isEqualTo("신규")
        assertThat(after.gradeLocked).isTrue()
        assertThat(after.totalPurchaseCount).isEqualTo(12)
    }

    @Test
    fun `매출 삭제로 구매횟수가 5에서 4로 떨어지면 단골에서 신규로 자동 강등된다`() {
        newTenant()
        val c = create()
        val saleIds = (1..5).map { addSale(c.id) }
        assertThat(gradeName(c.id)).isEqualTo("단골")

        saleService.delete(saleIds.last()) // 5 → 4

        assertThat(gradeName(c.id)).isEqualTo("신규")
    }
}
