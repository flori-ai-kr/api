package kr.ai.flori.customers.repository

import kr.ai.flori.customers.entity.Customer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CustomerRepository : JpaRepository<Customer, UUID> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Customer?

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<Customer>

    fun findByUserIdAndPhone(
        userId: UUID,
        phone: String,
    ): Customer?

    fun findTop10ByUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
        userId: UUID,
        name: String,
    ): List<Customer>

    fun findFirstByUserIdAndPhoneAndIdNot(
        userId: UUID,
        phone: String,
        id: UUID,
    ): Customer?

    fun findFirstByUserIdAndPhone(
        userId: UUID,
        phone: String,
    ): Customer?
}
