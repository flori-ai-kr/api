package kr.ai.flori.customers.repository

import kr.ai.flori.customers.entity.CustomerGrade
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerGradeRepository : JpaRepository<CustomerGrade, Long> {
    fun findByUserIdOrderBySortOrderAsc(userId: Long): List<CustomerGrade>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): CustomerGrade?

    fun countByUserId(userId: Long): Long

    fun existsByUserIdAndNameAndIdNot(
        userId: Long,
        name: String,
        id: Long,
    ): Boolean

    fun existsByUserIdAndName(
        userId: Long,
        name: String,
    ): Boolean
}
