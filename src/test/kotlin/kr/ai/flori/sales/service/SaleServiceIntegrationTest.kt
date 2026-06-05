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
    lateinit var photoCardRepository: PhotoCardRepository

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

    private fun cardSale(date: LocalDate = LocalDate.of(2026, 5, 22)) =
        SaleCreateRequest(
            date = date,
            productCategory = "basic_bouquet",
            amount = 100_000,
            paymentMethod = "card",
        )

    @Test
    fun `카드 매출을 생성한다`() {
        newTenant()
        val sale = saleService.create(cardSale())

        assertThat(sale.paymentMethod).isEqualTo("card")
        assertThat(sale.amount).isEqualTo(100_000)
        assertThat(sale.isUnpaid).isFalse()
    }

    @Test
    fun `현금 매출을 생성한다`() {
        newTenant()
        val sale =
            saleService.create(
                SaleCreateRequest(LocalDate.of(2026, 5, 22), "vase", 30_000, "cash"),
            )
        assertThat(sale.paymentMethod).isEqualTo("cash")
        assertThat(sale.isUnpaid).isFalse()
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
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 2), "basket", 2_000, "card"))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 3), "vase", 3_000, "transfer"))

        val cashOnly = saleService.list(null, null, null, 0, 100, null, listOf("cash"), null, null)
        assertThat(cashOnly.sales).hasSize(1)

        val vaseCategory = saleService.list(null, null, null, 0, 100, listOf("vase"), null, null, null)
        assertThat(vaseCategory.sales).hasSize(2)

        val firstPage = saleService.list(null, null, null, 0, 2, null, null, null, null)
        assertThat(firstPage.sales).hasSize(2)
        assertThat(firstPage.hasMore).isTrue()
        val secondPage = saleService.list(null, null, null, 2, 2, null, null, null, null)
        assertThat(secondPage.sales).hasSize(1)
        assertThat(secondPage.hasMore).isFalse()
    }

    @Test
    fun `요약은 DB 집계로 결제수단별 합계와 전체(미수 포함)를 계산하고 동일 필터를 지원한다`() {
        newTenant()
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 1), "vase", 1_000, "card", customerName = "김하늘"))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 2), "basket", 2_000, "cash"))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 3), "vase", 3_000, "transfer"))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 4), "vase", 4_000, "naverpay"))
        saleService.create(SaleCreateRequest(LocalDate.of(2026, 5, 5), "vase", 5_000, "unpaid"))

        // 전체: total/count는 미수 포함, 버킷은 결제수단별
        val all = saleService.summary("2026-05", null, null, null, null, null, null)
        assertThat(all.total).isEqualTo(15_000L)
        assertThat(all.card).isEqualTo(1_000L)
        assertThat(all.cash).isEqualTo(2_000L)
        assertThat(all.transfer).isEqualTo(3_000L)
        assertThat(all.naverpay).isEqualTo(4_000L)
        assertThat(all.count).isEqualTo(5L)

        // 결제수단 IN
        val cardOnly = saleService.summary("2026-05", null, null, null, listOf("card"), null, null)
        assertThat(cardOnly.total).isEqualTo(1_000L)
        assertThat(cardOnly.count).isEqualTo(1L)

        // 카테고리 IN
        val vaseOnly = saleService.summary("2026-05", null, null, listOf("vase"), null, null, null)
        assertThat(vaseOnly.count).isEqualTo(4L)
        assertThat(vaseOnly.total).isEqualTo(13_000L)

        // 채널 IN (기본 채널 other)
        val otherChannel = saleService.summary("2026-05", null, null, null, null, listOf("other"), null)
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
