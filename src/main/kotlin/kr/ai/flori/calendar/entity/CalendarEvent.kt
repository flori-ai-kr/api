package kr.ai.flori.calendar.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.LocalDate
import java.util.UUID

/**
 * 다중일 캘린더 이벤트. 멀티테넌시: user_id 격리.
 */
@Entity
@Table(name = "calendar_events")
class CalendarEvent(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "color", nullable = false)
    var color: String = "#f43f5e"

    @Column(name = "description")
    var description: String? = null
}
