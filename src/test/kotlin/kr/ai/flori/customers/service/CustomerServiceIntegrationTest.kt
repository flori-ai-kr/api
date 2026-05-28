package kr.ai.flori.customers.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerCreateRequest
import kr.ai.flori.customers.dto.CustomerUpdateRequest
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.service.SaleService
import kr.ai.flori.support.TestAccounts
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
class CustomerServiceIntegrationTest {
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

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "cust-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun create(
        name: String = "홍길동",
        phone: String = "01012345678",
    ) = customerService.create(CustomerCreateRequest(name = name, phone = phone))

    @Test
    fun `생성 시 기본 등급은 new이며 통계는 0이다`() {
        newTenant()
        val customer = create()
        assertThat(customer.grade).isEqualTo("new")
        assertThat(customer.totalPurchaseCount).isZero()
    }

    @Test
    fun `중복 전화번호 생성은 DUPLICATE`() {
        newTenant()
        create(phone = "01011112222")
        assertThatThrownBy { create(name = "다른사람", phone = "01011112222") }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.CONFLICT)
            }
    }

    @Test
    fun `등급 변경과 부분 수정이 동작한다`() {
        newTenant()
        val c = create()
        assertThat(customerService.updateGrade(c.id, "vip").grade).isEqualTo("vip")
        assertThat(customerService.update(c.id, CustomerUpdateRequest(note = "단골")).note).isEqualTo("단골")
    }

    @Test
    fun `잘못된 등급은 거부된다`() {
        newTenant()
        val c = create()
        assertThatThrownBy { customerService.updateGrade(c.id, "platinum") }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `findOrCreate는 같은 전화번호면 기존 고객을 반환한다`() {
        newTenant()
        val first = customerService.findOrCreate("홍길동", "01099998888")
        val second = customerService.findOrCreate("다른이름", "01099998888")
        assertThat(second.id).isEqualTo(first.id)
    }

    @Test
    fun `구매 통계는 매출에서 실시간 집계된다`() {
        newTenant()
        val customer = create()
        saleService.create(saleFor(customer.id, LocalDate.of(2026, 5, 1), 10_000))
        saleService.create(saleFor(customer.id, LocalDate.of(2026, 5, 10), 20_000))

        val withStats = customerService.get(customer.id)
        assertThat(withStats.totalPurchaseCount).isEqualTo(2)
        assertThat(withStats.totalPurchaseAmount).isEqualTo(30_000)
        assertThat(withStats.firstPurchaseDate).isEqualTo(LocalDate.of(2026, 5, 1))
        assertThat(withStats.lastPurchaseDate).isEqualTo(LocalDate.of(2026, 5, 10))

        val sales = customerService.getCustomerSales(customer.id, 0, 10)
        assertThat(sales.sales).hasSize(2)
    }

    @Test
    fun `이름 검색은 부분일치로 찾는다`() {
        newTenant()
        create(name = "김철수", phone = "01000000001")
        create(name = "김영희", phone = "01000000002")
        assertThat(customerService.searchByName("김")).hasSize(2)
        assertThat(customerService.searchByName("철수")).hasSize(1)
    }

    @Test
    fun `다른 테넌트의 고객은 조회할 수 없다`() {
        newTenant()
        val mine = create()
        newTenant()
        assertThatThrownBy { customerService.get(mine.id) }.isInstanceOf(AppException::class.java)
        assertThat(customerService.list()).isEmpty()
    }

    private fun saleFor(
        customerId: Long,
        date: LocalDate,
        amount: Int,
    ) = SaleCreateRequest(
        date = date,
        productCategory = "basic_bouquet",
        amount = amount,
        paymentMethod = "cash",
        customerId = customerId,
    )
}
