package kr.ai.flori.reservations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * 예약(픽업). 매출과 양방향 연결(sale_id). 멀티테넌시: user_id 격리.
 * reminder_sent/pickup_completed는 스케줄 푸시·픽업 처리용 플래그.
 */
@Entity
@Table(name = "reservations")
class Reservation(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "date", nullable = false)
    var date: LocalDate,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "time")
    var time: LocalTime? = null

    @Column(name = "customer_name", nullable = false)
    var customerName: String = ""

    @Column(name = "customer_phone")
    var customerPhone: String? = null

    @Column(name = "title", nullable = false)
    var title: String = ""

    @Column(name = "memo")
    var memo: String? = null

    @Column(name = "status", nullable = false)
    var status: String = "pending"

    @Column(name = "sale_id")
    var saleId: Long? = null

    @Column(name = "amount")
    var amount: Int = 0

    @Column(name = "reminder_at")
    var reminderAt: Instant? = null

    @Column(name = "reminder_sent", nullable = false)
    var reminderSent: Boolean = false

    @Column(name = "pickup_completed", nullable = false)
    var pickupCompleted: Boolean = false
}
