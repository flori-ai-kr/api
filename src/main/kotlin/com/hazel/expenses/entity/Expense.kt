package com.hazel.expenses.entity

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
 * 지출. total_amount = unit_price * quantity 는 서버가 계산하는 SSOT.
 * recurring_id 가 있으면 고정비 자동생성/연동 인스턴스.
 */
@Entity
@Table(name = "expenses")
class Expense(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "date", nullable = false)
    var date: LocalDate,
    @Column(name = "item_name", nullable = false)
    var itemName: String,
    @Column(name = "category", nullable = false)
    var category: String,
    @Column(name = "unit_price", nullable = false)
    var unitPrice: Int,
    @Column(name = "quantity", nullable = false)
    var quantity: Int,
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Int,
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "card_company")
    var cardCompany: String? = null

    @Column(name = "vendor")
    var vendor: String? = null

    @Column(name = "note")
    var note: String? = null

    @Column(name = "recurring_id")
    var recurringId: UUID? = null

    @Column(name = "is_recurring_modified", nullable = false)
    var isRecurringModified: Boolean = false

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
