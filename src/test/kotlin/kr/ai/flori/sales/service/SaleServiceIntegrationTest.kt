package kr.ai.flori.sales.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.dto.SaleUpdateRequest
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
class SaleServiceIntegrationTest {
    @Autowired
    lateinit var saleService: SaleService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    /** 가입(기본 카드사 시드 포함) 후 해당 user를 TenantContext에 설정. */
    private fun newTenant(): UUID {
        val email = "sale-${UUID.randomUUID()}@flori.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun cardSale(date: LocalDate = LocalDate.of(2026, 5, 22)) =
        SaleCreateRequest(
            date = date,
            productCategory = "basic_bouquet",
            amount = 100_000,
            paymentMethod = "card",
            cardCompany = "신한카드",
        )

    @Test
    fun `카드 매출은 수수료·입금예정액·입금예정일을 서버가 계산한다`() {
        newTenant()
        val sale = saleService.create(cardSale())

        // 신한카드 기본값: 수수료율 2.0%, 입금 3영업일
        assertThat(sale.fee).isEqualTo(2_000)
        assertThat(sale.expectedDeposit).isEqualTo(98_000)
        assertThat(sale.expectedDepositDate).isEqualTo(LocalDate.of(2026, 5, 27))
        assertThat(sale.depositStatus).isEqualTo("pending")
        assertThat(sale.isUnpaid).isFalse()
    }

    @Test
    fun `현금 매출은 입금대조 대상이 아니다`() {
        newTenant()
        val sale =
            saleService.create(
                SaleCreateRequest(LocalDate.of(2026, 5, 22), "vase", 30_000, "cash"),
            )
        assertThat(sale.fee).isNull()
        assertThat(sale.depositStatus).isEqualTo("not_applicable")
    }

    @Test
    fun `미수 매출은 is_unpaid=true 이며 완료·되돌리기가 동작한다`() {
        newTenant()
        val unpaid =
            saleService.create(
                SaleCreateRequest(LocalDate.of(2026, 5, 22), "reservation", 50_000, "unpaid"),
            )
        assertThat(unpaid.isUnpaid).isTrue()

        val completed = saleService.completeUnpaid(unpaid.id, "card")
        assertThat(completed.paymentMethod).isEqualTo("card")
        assertThat(completed.isUnpaid).isTrue() // 마커 유지

        val reverted = saleService.revertUnpaid(unpaid.id)
        assertThat(reverted.paymentMethod).isEqualTo("unpaid")
    }

    @Test
    fun `미수가 아닌 매출의 완료 시도는 거부된다`() {
        newTenant()
        val sale = saleService.create(cardSale())
        assertThatThrownBy { saleService.completeUnpaid(sale.id, "cash") }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `목록은 다중선택 필터와 무한스크롤을 지원한다`() {
        newTenant()
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 1), "vase", 1_000, "cash"))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 2), "basket", 2_000, "card", cardCompany = "신한카드"))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 3), "vase", 3_000, "transfer"))

        val cashOnly = saleService.list(null, 0, 100, null, listOf("cash"), null, null)
        assertThat(cashOnly.sales).hasSize(1)

        val vaseCategory = saleService.list(null, 0, 100, listOf("vase"), null, null, null)
        assertThat(vaseCategory.sales).hasSize(2)

        val firstPage = saleService.list(null, 0, 2, null, null, null, null)
        assertThat(firstPage.sales).hasSize(2)
        assertThat(firstPage.hasMore).isTrue()
        val secondPage = saleService.list(null, 2, 2, null, null, null, null)
        assertThat(secondPage.sales).hasSize(1)
        assertThat(secondPage.hasMore).isFalse()
    }

    @Test
    fun `비고 자동완성은 빈도순으로 반환한다`() {
        newTenant()
        repeat(3) { saleService.create(cardSale().copy(note = "리본 포장")) }
        saleService.create(cardSale().copy(note = "당일 픽업"))

        val notes = saleService.suggestions()
        assertThat(notes).containsExactly("리본 포장", "당일 픽업")
    }

    @Test
    fun `다른 테넌트의 매출은 조회·수정할 수 없다`() {
        newTenant()
        val saleA = saleService.create(cardSale())

        // 다른 사용자로 전환
        newTenant()
        assertThatThrownBy { saleService.get(saleA.id) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.NOT_FOUND)
            }
        assertThatThrownBy { saleService.update(saleA.id, SaleUpdateRequest(amount = 1)) }
            .isInstanceOf(AppException::class.java)
        assertThat(saleService.list(null, 0, 100, null, null, null, null).sales).isEmpty()
    }
}
