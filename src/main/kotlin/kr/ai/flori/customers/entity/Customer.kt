package kr.ai.flori.customers.entity

import jakarta.persistence.*
import kr.ai.flori.common.entity.BaseEntity

/**
 * 고객. (phone, user_id) 복합 unique. 멀티테넌시: 모든 쿼리 user_id 격리.
 * 구매 통계(횟수/총액/최초·최근 구매일)는 sales에서 실시간 집계(SSOT)하므로 엔티티에 매핑하지 않는다.
 */
@Entity
@Table(name = "customers")
class Customer(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "phone", nullable = false)
    var phone: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "grade")
    var grade: String = "new"

    @Column(name = "gender")
    var gender: String? = null

    @Column(name = "note")
    var note: String? = null
}
