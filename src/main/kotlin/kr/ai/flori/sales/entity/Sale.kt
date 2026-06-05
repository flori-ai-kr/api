package kr.ai.flori.sales.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.LocalDate

/**
 * 매출. 멀티테넌시: 모든 조회/변경은 user_id로 격리한다(서비스/리포지토리에서 강제).
 */
@Entity
@Table(name = "sales")
class Sale(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "date", nullable = false)
    var date: LocalDate,
    @Column(name = "product_category")
    var productCategory: String?,
    @Column(name = "amount", nullable = false)
    var amount: Int,
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "reservation_channel")
    var reservationChannel: String = "other"

    @Column(name = "is_unpaid", nullable = false)
    var isUnpaid: Boolean = false

    @Column(name = "customer_name")
    var customerName: String? = null

    @Column(name = "customer_phone")
    var customerPhone: String? = null

    @Column(name = "customer_id")
    var customerId: Long? = null

    @Column(name = "memo")
    var memo: String? = null

    @Column(name = "has_review")
    var hasReview: Boolean = false
}
