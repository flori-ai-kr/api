package kr.ai.flori.expenses.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.LocalDate

/**
 * 지출. total_amount = unit_price * quantity 는 서버가 계산하는 SSOT.
 * recurring_id 가 있으면 고정비 자동생성/연동 인스턴스.
 */
@Entity
@Table(name = "expenses")
class Expense(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "date", nullable = false)
    var date: LocalDate,
    @Column(name = "item_name", nullable = false)
    var itemName: String,
    @Column(name = "category_id")
    var categoryId: Long?,
    @Column(name = "unit_price", nullable = false)
    var unitPrice: Int,
    @Column(name = "quantity", nullable = false)
    var quantity: Int,
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Int,
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "card_company")
    var cardCompany: String? = null

    @Column(name = "vendor")
    var vendor: String? = null

    @Column(name = "memo")
    var memo: String? = null

    @Column(name = "recurring_id")
    var recurringId: Long? = null

    @Column(name = "is_recurring_modified", nullable = false)
    var isRecurringModified: Boolean = false
}
