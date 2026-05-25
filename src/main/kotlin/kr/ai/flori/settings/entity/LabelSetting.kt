package kr.ai.flori.settings.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import java.util.UUID

/**
 * value/label/color/sort_order 구조를 공유하는 설정(매출/지출 카테고리·결제방식) 공통 베이스.
 * (value, user_id) 복합 unique. 멀티테넌시: user_id 격리.
 */
@MappedSuperclass
abstract class LabelSetting(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "value", nullable = false)
    var value: String = ""

    @Column(name = "label", nullable = false)
    var label: String = ""

    @Column(name = "color")
    var color: String = "#6b7280"

    @Column(name = "sort_order")
    var sortOrder: Int = 0
}

@Entity
@Table(name = "sale_categories")
class SaleCategory(
    userId: UUID,
) : LabelSetting(userId)

@Entity
@Table(name = "payment_methods")
class SalePaymentMethod(
    userId: UUID,
) : LabelSetting(userId)

@Entity
@Table(name = "expense_categories")
class ExpenseCategory(
    userId: UUID,
) : LabelSetting(userId)

@Entity
@Table(name = "expense_payment_methods")
class ExpensePaymentMethod(
    userId: UUID,
) : LabelSetting(userId)
