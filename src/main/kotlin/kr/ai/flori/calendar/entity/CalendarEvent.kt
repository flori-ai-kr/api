package kr.ai.flori.calendar.entity

import jakarta.persistence.*
import kr.ai.flori.common.entity.BaseEntity
import java.time.LocalDate

/**
 * 다중일 캘린더 이벤트. 멀티테넌시: user_id 격리.
 */
@Entity
@Table(name = "calendar_events")
class CalendarEvent(
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

    @Column(name = "description")
    var description: String? = null
}
