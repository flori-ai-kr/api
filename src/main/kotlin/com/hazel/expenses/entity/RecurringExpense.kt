package com.hazel.expenses.entity

import com.hazel.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.util.UUID

/** 매년 반복 일자(월/일). yearly_dates jsonb 요소. */
data class YearlyDate(
    val m: Int = 0,
    val d: Int = 0,
)

/**
 * 고정비(반복 지출) 템플릿. 다중값 반복 규칙(주/월/연).
 * 배열·jsonb 컬럼은 Hibernate 6 네이티브 매핑(@JdbcTypeCode)으로 validate 친화적으로 처리.
 */
@Entity
@Table(name = "recurring_expenses")
class RecurringExpense(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "item_name", nullable = false)
    var itemName: String,
    @Column(name = "category", nullable = false)
    var category: String,
    @Column(name = "unit_price", nullable = false)
    var unitPrice: Int,
    @Column(name = "quantity", nullable = false)
    var quantity: Int,
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: String,
    @Column(name = "frequency", nullable = false)
    var frequency: String,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "interval_count", nullable = false)
    var intervalCount: Int = 1

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", columnDefinition = "integer[]", nullable = false)
    var daysOfWeek: List<Int> = emptyList()

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_month", columnDefinition = "integer[]", nullable = false)
    var daysOfMonth: List<Int> = emptyList()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "yearly_dates", columnDefinition = "jsonb", nullable = false)
    var yearlyDates: List<YearlyDate> = emptyList()

    @Column(name = "end_date")
    var endDate: LocalDate? = null

    @Column(name = "vendor")
    var vendor: String? = null

    @Column(name = "note")
    var note: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}
