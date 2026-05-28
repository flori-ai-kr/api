package kr.ai.flori.customers.repository

import kr.ai.flori.customers.entity.Customer
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRepository : JpaRepository<Customer, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Customer?

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Customer>

    fun findByUserIdAndPhone(
        userId: Long,
        phone: String,
    ): Customer?

    fun findTop10ByUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
        userId: Long,
        name: String,
    ): List<Customer>

    fun findFirstByUserIdAndPhoneAndIdNot(
        userId: Long,
        phone: String,
        id: Long,
    ): Customer?

    fun findFirstByUserIdAndPhone(
        userId: Long,
        phone: String,
    ): Customer?
}
