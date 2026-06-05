package kr.ai.flori.schedules.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.LocalDate

@Entity
@Table(name = "schedules")
class Schedule(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "color", nullable = false)
    var color: String = "#f43f5e"

    @Column(name = "memo")
    var memo: String? = null
}
