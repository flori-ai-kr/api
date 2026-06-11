package kr.ai.flori.support

import kr.ai.flori.customers.entity.Customer
import kr.ai.flori.sales.entity.Sale
import kr.ai.flori.settings.repository.LabelSettingRepository
import java.time.LocalDate

/**
 * 도메인 엔티티 테스트 픽스처 — 합리적 기본값 + 필요한 것만 오버라이드.
 * FK 미사용 스키마라 라벨 id는 임의값으로도 저장되지만, 라벨 해석(이름·버킷 매핑)을 검증하는
 * 테스트는 labelId() 로 가입 시드의 실제 라벨 id를 조회해 사용할 것.
 */
object Fixtures {
    fun sale(
        userId: Long,
        date: LocalDate = LocalDate.of(2026, 6, 1),
        categoryId: Long? = 1L,
        amount: Int = 50_000,
        paymentMethodId: Long? = 1L,
        customerId: Long? = null,
        isUnpaid: Boolean = false,
        customerName: String? = null,
        memo: String? = null,
    ): Sale =
        Sale(userId = userId, date = date, categoryId = categoryId, amount = amount, paymentMethodId = paymentMethodId)
            .apply {
                this.customerId = customerId
                this.isUnpaid = isUnpaid
                this.customerName = customerName
                this.memo = memo
            }

    fun customer(
        userId: Long,
        name: String = "테스트고객",
        phone: String = "010${(10_000_000..99_999_999).random()}",
    ): Customer = Customer(userId, name, phone)

    /** 가입 시드로 생성된 실제 라벨 id 조회(예: SALE/PAYMENT/"cash"). 14개 테스트 파일 복붙 패턴의 SSOT. */
    fun labelId(
        labelSettingRepository: LabelSettingRepository,
        userId: Long,
        domain: String,
        kind: String,
        value: String,
    ): Long =
        requireNotNull(
            requireNotNull(labelSettingRepository.findByUserIdAndDomainAndKindAndValue(userId, domain, kind, value)) {
                "시드 라벨 없음: $domain/$kind/$value"
            }.id,
        )
}
