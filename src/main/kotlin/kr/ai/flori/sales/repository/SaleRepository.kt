package kr.ai.flori.sales.repository

import kr.ai.flori.sales.entity.Sale
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SaleRepository :
    JpaRepository<Sale, Long>,
    JpaSpecificationExecutor<Sale> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Sale?

    /** 고객별 매출(테넌트 격리). 고객 도메인(SPEC-007)에서 사용. */
    fun findByUserIdAndCustomerId(
        userId: Long,
        customerId: Long,
        pageable: Pageable,
    ): Page<Sale>

    /** 메모 자동완성: 빈도(사용 횟수) 내림차순. */
    @Query(
        "SELECT s.memo FROM Sale s " +
            "WHERE s.userId = :userId AND s.memo IS NOT NULL AND s.memo <> '' " +
            "GROUP BY s.memo ORDER BY COUNT(s.memo) DESC",
    )
    fun findMemosByFrequency(
        @Param("userId") userId: Long,
    ): List<String>

    /** 고객 삭제 시 해당 고객을 참조하던 매출의 customer_id를 NULL로(FK 미사용 — 앱이 참조 정리). */
    @Modifying
    @Query("UPDATE Sale s SET s.customerId = null WHERE s.userId = :userId AND s.customerId = :customerId")
    fun clearCustomerReference(
        @Param("userId") userId: Long,
        @Param("customerId") customerId: Long,
    ): Int
}
