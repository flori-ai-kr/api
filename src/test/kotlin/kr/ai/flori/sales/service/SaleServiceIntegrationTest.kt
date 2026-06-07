package kr.ai.flori.sales.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.photos.entity.PhotoCard
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.reservations.entity.Reservation
import kr.ai.flori.reservations.repository.ReservationRepository
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.dto.SaleUpdateRequest
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
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
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var reservationRepository: ReservationRepository

    @Autowired
    lateinit var customerRepository: kr.ai.flori.customers.repository.CustomerRepository

    @Autowired
    lateinit var photoCardRepository: PhotoCardRepository

    @Autowired
    lateinit var labelSettingRepository: LabelSettingRepository

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    /** 가입 후 해당 user를 TenantContext에 설정. */
    private fun newTenant(): Long {
        val email = "sale-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    /** 시드된 매출 카테고리 value → label_settings id. */
    private fun catId(value: String): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.SALE,
                LabelKinds.CATEGORY,
                value,
            ),
        ).id!!

    /** 시드된 매출 채널 value → label_settings id. */
    private fun channelId(value: String): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.SALE,
                LabelKinds.CHANNEL,
                value,
            ),
        ).id!!

    /** 시드된 매출 결제수단 value → label_settings id. */
    private fun payId(value: String): Long =
        requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.SALE,
                LabelKinds.PAYMENT,
                value,
            ),
        ).id!!

    private fun cardSale(date: LocalDate = LocalDate.of(2026, 5, 22)) =
        SaleCreateRequest(
            date = date,
            categoryId = catId("basic_bouquet"),
            amount = 100_000,
            paymentMethodId = payId("card"),
        )

    @Test
    fun `카드 매출을 생성한다`() {
        newTenant()
        val sale = saleService.create(cardSale())

        assertThat(sale.paymentMethodId).isEqualTo(payId("card"))
        assertThat(sale.amount).isEqualTo(100_000)
        assertThat(sale.isUnpaid).isFalse()
    }

    @Test
    fun `현금 매출을 생성한다`() {
        newTenant()
        val sale =
            saleService.create(
                SaleCreateRequest(LocalDate.of(2026, 5, 22), catId("vase"), 30_000, payId("cash")),
            )
        assertThat(sale.paymentMethodId).isEqualTo(payId("cash"))
        assertThat(sale.isUnpaid).isFalse()
    }

    @Test
    fun `미수 매출은 is_unpaid=true 이며 완료·되돌리기가 동작한다`() {
        newTenant()
        val unpaid =
            saleService.create(
                SaleCreateRequest(LocalDate.of(2026, 5, 22), catId("reservation"), 50_000, null, isUnpaid = true),
            )
        assertThat(unpaid.isUnpaid).isTrue()
        assertThat(unpaid.paymentMethodId).isNull()

        val completed = saleService.completeUnpaid(unpaid.id, payId("card"))
        assertThat(completed.paymentMethodId).isEqualTo(payId("card"))
        assertThat(completed.isUnpaid).isTrue() // 마커 유지

        val reverted = saleService.revertUnpaid(unpaid.id)
        assertThat(reverted.paymentMethodId).isNull()
        assertThat(reverted.isUnpaid).isTrue()
    }

    @Test
    fun `수정으로 결제완료 매출을 미수로 되돌리거나 다시 결제완료로 전환한다`() {
        newTenant()
        // 결제완료(카드) 매출
        val paid = saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 22), catId("reservation"), 50_000, payId("card")))
        assertThat(paid.isUnpaid).isFalse()
        assertThat(paid.paymentMethodId).isEqualTo(payId("card"))

        // 수정으로 미수 전환 → 결제수단 비고 마커 ON
        val toUnpaid = saleService.update(paid.id, SaleUpdateRequest(isUnpaid = true))
        assertThat(toUnpaid.isUnpaid).isTrue()
        assertThat(toUnpaid.paymentMethodId).isNull()

        // 수정으로 다시 결제완료 전환 → 마커 OFF + 결제수단 확정
        val toPaid = saleService.update(paid.id, SaleUpdateRequest(isUnpaid = false, paymentMethodId = payId("cash")))
        assertThat(toPaid.isUnpaid).isFalse()
        assertThat(toPaid.paymentMethodId).isEqualTo(payId("cash"))

        // isUnpaid=null 이면 미수 상태 불변(결제수단만 반영)
        val unchanged = saleService.update(paid.id, SaleUpdateRequest(amount = 60_000))
        assertThat(unchanged.isUnpaid).isFalse()
        assertThat(unchanged.amount).isEqualTo(60_000)
    }

    @Test
    fun `미수가 아닌 매출의 완료 시도는 거부된다`() {
        newTenant()
        val sale = saleService.create(cardSale())
        assertThatThrownBy { saleService.completeUnpaid(sale.id, payId("cash")) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `목록은 다중선택 필터와 무한스크롤을 지원한다`() {
        newTenant()
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 1), catId("vase"), 1_000, payId("cash")))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 2), catId("basket"), 2_000, payId("card")))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 3), catId("vase"), 3_000, payId("transfer")))

        val cashOnly = saleService.list(null, null, null, 0, 100, null, listOf(payId("cash")), null, null)
        assertThat(cashOnly.sales).hasSize(1)

        val vaseCategory = saleService.list(null, null, null, 0, 100, listOf(catId("vase")), null, null, null)
        assertThat(vaseCategory.sales).hasSize(2)

        val firstPage = saleService.list(null, null, null, 0, 2, null, null, null, null)
        assertThat(firstPage.sales).hasSize(2)
        assertThat(firstPage.hasMore).isTrue()
        val secondPage = saleService.list(null, null, null, 2, 2, null, null, null, null)
        assertThat(secondPage.sales).hasSize(1)
        assertThat(secondPage.hasMore).isFalse()
    }

    @Test
    fun `요약은 DB 집계로 결제수단별 합계와 전체(미수 제외)를 계산하고 동일 필터를 지원한다`() {
        newTenant()
        saleService.create(
            SaleCreateRequest(LocalDate.of(2026, 5, 1), catId("vase"), 1_000, payId("card"), customerName = "김하늘"),
        )
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 2), catId("basket"), 2_000, payId("cash")))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 3), catId("vase"), 3_000, payId("transfer")))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 4), catId("vase"), 4_000, payId("naverpay")))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 5), catId("vase"), 5_000, null, isUnpaid = true))

        // total은 미수(미정산: payment_method_id NULL) 제외, count는 전체, 버킷은 결제수단별
        val all = saleService.summary("2026-05", null, null, null, null, null, null)
        assertThat(all.total).isEqualTo(10_000L) // 15,000 - 미수 5,000
        assertThat(all.card).isEqualTo(1_000L)
        assertThat(all.cash).isEqualTo(2_000L)
        assertThat(all.transfer).isEqualTo(3_000L)
        assertThat(all.naverpay).isEqualTo(4_000L)
        assertThat(all.count).isEqualTo(5L)

        // 결제수단 IN
        val cardOnly = saleService.summary("2026-05", null, null, null, listOf(payId("card")), null, null)
        assertThat(cardOnly.total).isEqualTo(1_000L)
        assertThat(cardOnly.count).isEqualTo(1L)

        // 카테고리 IN (vase: card 1,000 + transfer 3,000 + naverpay 4,000 + 미수 5,000 → total은 미수 제외)
        val vaseOnly = saleService.summary("2026-05", null, null, listOf(catId("vase")), null, null, null)
        assertThat(vaseOnly.count).isEqualTo(4L)
        assertThat(vaseOnly.total).isEqualTo(8_000L)

        // 채널 IN (기본 채널 other)
        val otherChannel = saleService.summary("2026-05", null, null, null, null, listOf(channelId("other")), null)
        assertThat(otherChannel.count).isEqualTo(5L)

        // 검색(고객명)
        val searched = saleService.summary("2026-05", null, null, null, null, null, "김하늘")
        assertThat(searched.count).isEqualTo(1L)
        assertThat(searched.card).isEqualTo(1_000L)

        // month=null 누적: 전체 기간 합산
        assertThat(saleService.summary(null, null, null, null, null, null, null).count).isEqualTo(5L)

        // 다른 달 → 빈 합계
        val empty = saleService.summary("2026-04", null, null, null, null, null, null)
        assertThat(empty.total).isEqualTo(0L)
        assertThat(empty.count).isEqualTo(0L)
    }

    @Test
    fun `이름+전화번호로 등록한 신규고객은 자동 생성되어 매출에 연결된다`() {
        val userId = newTenant()
        val sale =
            saleService.create(
                cardSale().copy(customerName = "박서연", customerPhone = "01012345678"),
            )

        // 고객이 자동 생성되고 그 id가 매출에 연결되어야 한다
        val customer = requireNotNull(customerRepository.findByUserIdAndPhone(userId, "01012345678"))
        assertThat(sale.customerId).isEqualTo(customer.id)
        assertThat(customer.name).isEqualTo("박서연")
    }

    @Test
    fun `동일 전화번호의 두 번째 매출은 기존 고객을 재사용한다`() {
        val userId = newTenant()
        val first = saleService.create(cardSale().copy(customerName = "박서연", customerPhone = "01012345678"))
        val second = saleService.create(cardSale().copy(customerName = "박서연", customerPhone = "01012345678"))

        assertThat(second.customerId).isEqualTo(first.customerId)
        assertThat(customerRepository.findByUserIdAndPhone(userId, "01012345678")).isNotNull()
    }

    @Test
    fun `전화번호만 있고 이름이 없으면 고객을 생성하지 않는다`() {
        val userId = newTenant()
        val sale = saleService.create(cardSale().copy(customerPhone = "01099998888"))

        assertThat(sale.customerId).isNull()
        assertThat(customerRepository.findByUserIdAndPhone(userId, "01099998888")).isNull()
    }

    @Test
    fun `수정 시 이름+전화번호로 신규고객이 자동 생성되어 매출에 연결된다`() {
        val userId = newTenant()
        val sale = saleService.create(cardSale()) // 고객 없이 생성
        assertThat(sale.customerId).isNull()

        val updated =
            saleService.update(sale.id, SaleUpdateRequest(customerName = "최유진", customerPhone = "01055556666"))

        val customer = requireNotNull(customerRepository.findByUserIdAndPhone(userId, "01055556666"))
        assertThat(updated.customerId).isEqualTo(customer.id)
        assertThat(customer.name).isEqualTo("최유진")
    }

    @Test
    fun `수정으로 전화번호를 바꾸면 새 전화번호의 고객으로 재연결된다`() {
        val userId = newTenant()
        val sale = saleService.create(cardSale().copy(customerName = "한별", customerPhone = "01011112222"))
        val firstCustomerId = sale.customerId
        assertThat(firstCustomerId).isNotNull()

        val updated =
            saleService.update(sale.id, SaleUpdateRequest(customerName = "한별", customerPhone = "01033334444"))

        val newCustomer = requireNotNull(customerRepository.findByUserIdAndPhone(userId, "01033334444"))
        assertThat(updated.customerId).isEqualTo(newCustomer.id)
        assertThat(updated.customerId).isNotEqualTo(firstCustomerId)
    }

    @Test
    fun `customerName·customerPhone을 건드리지 않는 수정은 기존 고객 연결을 유지한다`() {
        newTenant()
        val sale = saleService.create(cardSale().copy(customerName = "지우", customerPhone = "01077778888"))
        val linkedId = sale.customerId
        assertThat(linkedId).isNotNull()

        val updated = saleService.update(sale.id, SaleUpdateRequest(amount = 200_000))
        assertThat(updated.customerId).isEqualTo(linkedId)
        assertThat(updated.amount).isEqualTo(200_000)
    }

    @Test
    fun `명시한 customerId가 있으면 전화번호 자동연결보다 우선한다`() {
        val userId = newTenant()
        val existing =
            customerRepository.save(
                kr.ai.flori.customers.entity
                    .Customer(userId, "기존고객", "01000000000"),
            )
        val sale =
            saleService.create(
                cardSale().copy(customerId = existing.id, customerName = "다른이름", customerPhone = "01012345678"),
            )

        assertThat(sale.customerId).isEqualTo(existing.id)
    }

    @Test
    fun `비고 자동완성은 빈도순으로 반환한다`() {
        newTenant()
        repeat(3) { saleService.create(cardSale().copy(memo = "리본 포장")) }
        saleService.create(cardSale().copy(memo = "당일 픽업"))

        val memos = saleService.suggestions()
        assertThat(memos).containsExactly("리본 포장", "당일 픽업")
    }

    @Test
    fun `매출을 삭제하면 예약·사진카드는 보존되고 sale_id만 NULL이 된다`() {
        val userId = newTenant()
        val sale = saleService.create(cardSale())

        val reservation =
            reservationRepository.saveAndFlush(
                Reservation(userId, LocalDate.of(2026, 5, 22)).apply { saleId = sale.id },
            )
        val photoCard =
            photoCardRepository.saveAndFlush(
                PhotoCard(userId, "졸업식 부케").apply { saleId = sale.id },
            )

        saleService.delete(sale.id)

        // FK 미사용: 예약·사진카드는 보존되고 sale_id만 NULL(앱 레벨 참조 정리).
        val resAfter = requireNotNull(reservationRepository.findById(reservation.id!!).orElse(null))
        val cardAfter = requireNotNull(photoCardRepository.findById(photoCard.id!!).orElse(null))
        assertThat(resAfter.saleId).isNull()
        assertThat(cardAfter.saleId).isNull()
    }

    @Test
    fun `다른 테넌트의 매출은 조회·수정할 수 없다`() {
        newTenant()
        val saleA = saleService.create(cardSale())

        // 다른 사용자로 전환
        newTenant()
        assertThatThrownBy { saleService.get(saleA.id) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            }
        assertThatThrownBy { saleService.update(saleA.id, SaleUpdateRequest(amount = 1)) }
            .isInstanceOf(AppException::class.java)
        assertThat(saleService.list(null, null, null, 0, 100, null, null, null, null).sales).isEmpty()
    }
}
