package kr.ai.flori.expenses.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import java.time.LocalDate

/**
 * 고정비 skip 마커. "이것만 삭제" 시 자동생성 재발을 막는다.
 */
@Entity
@Table(name = "recurring_skips")
class RecurringSkip(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "recurring_id", nullable = false)
    var recurringId: Long,
    @Column(name = "skip_date", nullable = false)
    var skipDate: LocalDate,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}
