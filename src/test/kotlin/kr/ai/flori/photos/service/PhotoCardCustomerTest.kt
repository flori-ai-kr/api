package kr.ai.flori.photos.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerCreateRequest
import kr.ai.flori.customers.service.CustomerService
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.dto.PhotoCardUpdateRequest
import kr.ai.flori.photos.entity.PhotoCard
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.sales.repository.SaleRepository
import kr.ai.flori.support.Fixtures
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class PhotoCardCustomerTest {
    @Autowired
    lateinit var photoCardService: PhotoCardService

    @Autowired
    lateinit var photoCardRepository: PhotoCardRepository

    @Autowired
    lateinit var customerService: CustomerService

    @Autowired
    lateinit var saleRepository: SaleRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "photo-cust-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun newCustomer(name: String = "김민지"): Long =
        customerService.create(CustomerCreateRequest(name = name, phone = "010-${(1000..9999).random()}-${(1000..9999).random()}")).id

    @Test
    fun `유효한 customerId로 카드를 생성하면 customerId와 customerName이 응답에 담긴다`() {
        newTenant()
        val customerId = newCustomer("박서연")
        val card = photoCardService.create(PhotoCardCreateRequest(title = "부케", customerId = customerId))
        assertThat(card.customerId).isEqualTo(customerId)
        assertThat(card.customerName).isEqualTo("박서연")

        val fetched = photoCardService.get(card.id)
        assertThat(fetched.customerId).isEqualTo(customerId)
        assertThat(fetched.customerName).isEqualTo("박서연")
    }

    @Test
    fun `다른 테넌트의 customerId로 생성하면 예외`() {
        newTenant()
        val otherCustomerId = newCustomer("타인고객")
        newTenant()
        assertThatThrownBy {
            photoCardService.create(PhotoCardCreateRequest(title = "부케", customerId = otherCustomerId))
        }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `다른 테넌트의 customerId로 수정하면 예외`() {
        newTenant()
        val otherCustomerId = newCustomer("타인고객2")
        newTenant()
        val card = photoCardService.create(PhotoCardCreateRequest(title = "부케"))
        assertThatThrownBy {
            photoCardService.update(card.id, PhotoCardUpdateRequest(customerId = otherCustomerId))
        }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `customerId 필터는 sales 연동 없이 직접 컬럼으로 동작한다`() {
        newTenant()
        val customerId = newCustomer("직접연결")
        // 매출 연동 없이(saleId null) 고객에만 직접 연결
        val linked = photoCardService.create(PhotoCardCreateRequest(title = "직접연결카드", customerId = customerId))
        photoCardService.create(PhotoCardCreateRequest(title = "무관카드"))

        val page = photoCardService.list(null, null, customerId.toString())
        assertThat(page.cards).hasSize(1)
        assertThat(page.cards.first().id).isEqualTo(linked.id)
        assertThat(page.cards.first().customerName).isEqualTo("직접연결")
    }

    @Test
    fun `고객 삭제 시 연결된 카드의 customer_id가 NULL로 풀린다`() {
        val userId = newTenant()
        val customerId = newCustomer("삭제대상")
        val card = photoCardService.create(PhotoCardCreateRequest(title = "보존카드", customerId = customerId))

        customerService.delete(customerId)

        val remaining = photoCardRepository.findByIdAndUserId(card.id, userId)
        assertThat(remaining).isNotNull
        assertThat(remaining!!.customerId).isNull()
    }

    @Test
    fun `수정으로 customerId를 null로 해제할 수 있다`() {
        newTenant()
        val customerId = newCustomer("해제대상")
        val card = photoCardService.create(PhotoCardCreateRequest(title = "해제카드", customerId = customerId))
        assertThat(card.customerId).isEqualTo(customerId)

        val cleared = photoCardService.update(card.id, PhotoCardUpdateRequest(customerId = null, clearCustomer = true))
        assertThat(cleared.customerId).isNull()
        assertThat(cleared.customerName).isNull()
    }

    @Test
    fun `saleId만 주고 생성하면 그 매출의 고객을 상속한다`() {
        val userId = newTenant()
        val customerId = newCustomer("매출고객")
        val sale = saleRepository.save(Fixtures.sale(userId, customerId = customerId))

        // 매출/캘린더 플로우: 사진은 saleId만 보내고 customerId는 보내지 않는다.
        val card = photoCardService.create(PhotoCardCreateRequest(title = "매출사진", saleId = sale.id))

        assertThat(card.customerId).isEqualTo(customerId)
        assertThat(card.customerName).isEqualTo("매출고객")
    }

    @Test
    fun `생성 시 명시한 customerId가 매출 고객보다 우선한다`() {
        val userId = newTenant()
        val saleCustomerId = newCustomer("매출고객")
        val explicitCustomerId = newCustomer("직접지정고객")
        val sale = saleRepository.save(Fixtures.sale(userId, customerId = saleCustomerId))

        val card =
            photoCardService.create(
                PhotoCardCreateRequest(title = "사진", saleId = sale.id, customerId = explicitCustomerId),
            )

        assertThat(card.customerId).isEqualTo(explicitCustomerId)
    }

    @Test
    fun `고객 없는 매출에 사진을 연결하면 customerId는 null이다`() {
        val userId = newTenant()
        val sale = saleRepository.save(Fixtures.sale(userId, customerId = null))

        val card = photoCardService.create(PhotoCardCreateRequest(title = "익명매출사진", saleId = sale.id))

        assertThat(card.customerId).isNull()
    }

    @Test
    fun `매출 연동 카드를 고객 신호 없이 수정하면 매출 고객으로 백필된다`() {
        val userId = newTenant()
        val customerId = newCustomer("백필고객")
        val sale = saleRepository.save(Fixtures.sale(userId, customerId = customerId))
        // 과거 데이터 재현: saleId는 있으나 customerId가 비어 있는 카드를 직접 저장
        val legacy = photoCardRepository.save(PhotoCard(userId, "레거시카드").apply { saleId = sale.id })
        assertThat(legacy.customerId).isNull()

        // 고객 지정/해제 신호 없이 제목만 수정(= createOrUpdatePhotoCardForSale PATCH 경로)
        val updated = photoCardService.update(requireNotNull(legacy.id), PhotoCardUpdateRequest(title = "수정됨"))

        assertThat(updated.customerId).isEqualTo(customerId)
        assertThat(updated.customerName).isEqualTo("백필고객")
    }

    @Test
    fun `이미 연결된 고객은 고객 신호 없는 수정으로 매출 고객에 덮어써지지 않는다`() {
        val userId = newTenant()
        val saleCustomerId = newCustomer("매출고객")
        val manualCustomerId = newCustomer("수동연결고객")
        val sale = saleRepository.save(Fixtures.sale(userId, customerId = saleCustomerId))
        // 매출 고객과 다른 고객을 수동으로 연결한 카드
        val card =
            photoCardService.create(
                PhotoCardCreateRequest(title = "수동카드", saleId = sale.id, customerId = manualCustomerId),
            )
        assertThat(card.customerId).isEqualTo(manualCustomerId)

        // 고객 신호 없이 제목만 수정 → 기존 수동 연결 유지(매출 고객으로 덮어쓰지 않음)
        val updated = photoCardService.update(card.id, PhotoCardUpdateRequest(title = "제목만변경"))

        assertThat(updated.customerId).isEqualTo(manualCustomerId)
    }
}
