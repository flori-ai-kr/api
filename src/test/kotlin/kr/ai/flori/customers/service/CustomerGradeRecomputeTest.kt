package kr.ai.flori.customers.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerCreateRequest
import kr.ai.flori.customers.repository.CustomerGradeRepository
import kr.ai.flori.sales.dto.SaleCreateRequest
import kr.ai.flori.sales.service.SaleService
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
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.util.UUID

/**
 * 등급 자동 재계산/수동 고정/되돌리기 + 응답(등급명·대표사진) 검증.
 * 기본 시드 등급: 신규(0) / 단골(5) / VIP(10) / 블랙리스트(수동).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class CustomerGradeRecomputeTest {
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

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "recompute-${UUID.randomUUID()}@flori.dev"
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

    private fun create(phone: String = "0101111${(1000..9999).random()}") =
        customerService.create(CustomerCreateRequest(name = "홍길동", phone = phone))

    /** 고객에게 매출 n건 추가. */
    private fun addSales(
        customerId: Long,
        n: Int,
    ) {
        val cat = catId()
        val pay = payId()
        repeat(n) {
            saleService.create(
                SaleCreateRequest(
                    date = LocalDate.of(2026, 5, 1),
                    categoryId = cat,
                    amount = 10_000,
                    paymentMethodId = pay,
                    customerId = customerId,
                ),
            )
        }
    }

    private fun gradeName(customerId: Long): String? = customerService.get(customerId).grade

    @Test
    fun `구매횟수 경계에 따라 자동 등급이 매겨진다 (0,4,5,10)`() {
        newTenant()

        // 0건 → 신규
        val c0 = create()
        customerService.recomputeGrade(c0.id)
        assertThat(gradeName(c0.id)).isEqualTo("신규")

        // 4건 → 신규 (단골 임계 5 미만)
        val c4 = create()
        addSales(c4.id, 4)
        customerService.recomputeGrade(c4.id)
        assertThat(gradeName(c4.id)).isEqualTo("신규")

        // 5건 → 단골
        val c5 = create()
        addSales(c5.id, 5)
        customerService.recomputeGrade(c5.id)
        assertThat(gradeName(c5.id)).isEqualTo("단골")

        // 10건 → VIP
        val c10 = create()
        addSales(c10.id, 10)
        customerService.recomputeGrade(c10.id)
        assertThat(gradeName(c10.id)).isEqualTo("VIP")
    }

    @Test
    fun `잠금 상태면 높은 구매횟수에도 자동 재계산이 등급을 바꾸지 않는다`() {
        val userId = newTenant()
        val c = create()
        val newGradeId = requireNotNull(gradeRepository.findByUserIdOrderBySortOrderAsc(userId).first { it.name == "신규" }.id)

        // 수동으로 신규 고정
        customerService.updateGrade(c.id, newGradeId)
        addSales(c.id, 12)
        customerService.recomputeGrade(c.id)

        val after = customerService.get(c.id)
        assertThat(after.grade).isEqualTo("신규")
        assertThat(after.gradeLocked).isTrue()
        assertThat(after.totalPurchaseCount).isEqualTo(12)
    }

    @Test
    fun `updateGrade는 등급을 잠근다`() {
        val userId = newTenant()
        val c = create()
        val vipId = requireNotNull(gradeRepository.findByUserIdOrderBySortOrderAsc(userId).first { it.name == "VIP" }.id)

        val updated = customerService.updateGrade(c.id, vipId)
        assertThat(updated.grade).isEqualTo("VIP")
        assertThat(updated.gradeId).isEqualTo(vipId)
        assertThat(updated.gradeLocked).isTrue()
    }

    @Test
    fun `revertGradeToAuto는 잠금을 해제하고 구매횟수 기준으로 재계산한다`() {
        val userId = newTenant()
        val c = create()
        addSales(c.id, 5) // 단골 자격
        val newGradeId = requireNotNull(gradeRepository.findByUserIdOrderBySortOrderAsc(userId).first { it.name == "신규" }.id)

        // 잘못/임의 수동 지정으로 신규 고정
        customerService.updateGrade(c.id, newGradeId)
        assertThat(customerService.get(c.id).grade).isEqualTo("신규")

        val reverted = customerService.revertGradeToAuto(c.id)
        assertThat(reverted.gradeLocked).isFalse()
        assertThat(reverted.grade).isEqualTo("단골")
    }

    @Test
    fun `list과 get 응답에 등급명·대표썸네일(최대6)·사진카운트가 포함된다`() {
        val userId = newTenant()
        val c = create()
        addSales(c.id, 5)
        customerService.recomputeGrade(c.id)

        // 사진첩 카드 4장(각 1장 이상의 photo) — 대표 썸네일은 최신순 최대 6개이므로 4장 모두 반환된다.
        repeat(4) { i ->
            jdbcTemplate.update(
                """
                INSERT INTO photo_cards (user_id, title, photos, customer_id, created_at)
                VALUES (?::bigint, ?, ?::jsonb, ?::bigint, NOW() + (? || ' seconds')::interval)
                """.trimIndent(),
                userId,
                "card$i",
                """[{"url":"https://cdn/u$i.jpg","originalName":"u$i.jpg"}]""",
                c.id,
                i, // 단조 증가 created_at → i=3 이 최신
            )
        }

        val fromGet = customerService.get(c.id)
        assertThat(fromGet.grade).isEqualTo("단골")
        assertThat(fromGet.photoCount).isEqualTo(4)
        assertThat(fromGet.photoThumbnails).hasSize(4)
        // 최신순 4장: u3, u2, u1, u0
        assertThat(fromGet.photoThumbnails).containsExactly(
            "https://cdn/u3.jpg",
            "https://cdn/u2.jpg",
            "https://cdn/u1.jpg",
            "https://cdn/u0.jpg",
        )

        val fromList = customerService.list().first { it.id == c.id }
        assertThat(fromList.grade).isEqualTo("단골")
        assertThat(fromList.photoCount).isEqualTo(4)
        assertThat(fromList.photoThumbnails).hasSize(4)
        assertThat(fromList.photoThumbnails.first()).isEqualTo("https://cdn/u3.jpg")
    }
}
