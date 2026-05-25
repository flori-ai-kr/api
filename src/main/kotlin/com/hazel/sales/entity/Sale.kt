package com.hazel.sales.entity

import com.hazel.common.domain.DepositStatuses
import com.hazel.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 매출. 멀티테넌시: 모든 조회/변경은 user_id로 격리한다(서비스/리포지토리에서 강제).
 * 카드수수료/입금예정일/입금상태는 서버가 계산하는 SSOT 값(앱은 표시만).
 *
 * photos(TEXT[])·reservation_id·deposited_at 컬럼은 각각 SPEC-010/008/009에서 매핑한다.
 */
@Entity
@Table(name = "sales")
class Sale(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "date", nullable = false)
    var date: LocalDate,
    @Column(name = "product_name", nullable = false)
    var productName: String,
    @Column(name = "product_category")
    var productCategory: String?,
    @Column(name = "amount", nullable = false)
    var amount: Int,
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "reservation_channel")
    var reservationChannel: String = "other"

    @Column(name = "is_unpaid", nullable = false)
    var isUnpaid: Boolean = false

    @Column(name = "card_company")
    var cardCompany: String? = null

    @Column(name = "fee")
    var fee: Int? = null

    @Column(name = "expected_deposit")
    var expectedDeposit: Int? = null

    @Column(name = "expected_deposit_date")
    var expectedDepositDate: LocalDate? = null

    @Column(name = "deposit_status")
    var depositStatus: String = "not_applicable"

    @Column(name = "deposited_at")
    var depositedAt: Instant? = null

    @Column(name = "customer_name")
    var customerName: String? = null

    @Column(name = "customer_phone")
    var customerPhone: String? = null

    @Column(name = "customer_id")
    var customerId: UUID? = null

    @Column(name = "note")
    var note: String? = null

    @Column(name = "has_review")
    var hasReview: Boolean = false

    /**
     * 입금 완료 처리(입금대조 도메인). 상태·시각을 함께 전이해 불변식을 한곳에 둔다.
     * 다중 필드 전이는 서비스가 흩뿌리지 않고 엔티티 메서드로 캡슐화한다(엔티티 업데이트 컨벤션).
     */
    fun markDepositCompleted() {
        depositStatus = DepositStatuses.COMPLETED
        depositedAt = Instant.now()
    }

    /** 입금 완료 되돌리기. */
    fun revertDeposit() {
        depositStatus = DepositStatuses.PENDING
        depositedAt = null
    }
}
