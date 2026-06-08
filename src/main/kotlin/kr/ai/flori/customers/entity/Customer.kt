package kr.ai.flori.customers.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
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

    // 등급(커스텀 등급 테이블 참조). 실제 자동승급/잠금 로직은 Task 5에서 도입.
    @Column(name = "grade_id")
    var gradeId: Long? = null

    @Column(name = "grade_locked", nullable = false)
    var gradeLocked: Boolean = false

    // 레거시 등급 문자열 브리지(컬럼은 마이그레이션에서 제거됨). 비영속 — Task 5에서 등급 도메인으로 대체 예정.
    @Transient
    var grade: String = "new"

    @Column(name = "gender")
    var gender: String? = null

    @Column(name = "memo")
    var memo: String? = null
}
