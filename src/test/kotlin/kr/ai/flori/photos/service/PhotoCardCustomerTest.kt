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
import kr.ai.flori.photos.repository.PhotoCardRepository
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
}
