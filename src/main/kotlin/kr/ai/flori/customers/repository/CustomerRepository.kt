package kr.ai.flori.customers.repository

import kr.ai.flori.customers.entity.Customer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CustomerRepository : JpaRepository<Customer, Long> {
    /**
     * 등급 삭제 시 해당 등급을 참조하던 고객의 grade_id를 NULL로(테넌트 격리).
     * Customer 엔티티의 gradeId 필드가 아직 없을 수 있어(Task 5 도입) 네이티브 쿼리로 컬럼을 직접 갱신한다.
     */
    @Modifying
    @Query(
        value = "UPDATE customers SET grade_id = NULL WHERE user_id = :userId AND grade_id = :gradeId",
        nativeQuery = true,
    )
    fun clearGradeReference(
        @Param("userId") userId: Long,
        @Param("gradeId") gradeId: Long,
    ): Int

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
