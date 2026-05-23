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
 * 고정비 skip 마커. "이것만 삭제" 시 자동생성 재발을 막는다.
 */
@Entity
@Table(name = "recurring_skips")
class RecurringSkip(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "recurring_id", nullable = false)
    var recurringId: UUID,
    @Column(name = "skip_date", nullable = false)
    var skipDate: LocalDate,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
