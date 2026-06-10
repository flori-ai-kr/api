package kr.ai.flori.customers.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/** 테넌트별 고객 등급 정의. threshold NULL = 수동 전용. (user_id, name) unique. */
@Entity
@Table(name = "customer_grades")
class CustomerGrade(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "name", nullable = false)
    var name: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "threshold")
    var threshold: Int? = null

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
}
