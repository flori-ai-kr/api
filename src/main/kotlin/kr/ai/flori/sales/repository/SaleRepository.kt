package kr.ai.flori.sales.repository

import kr.ai.flori.sales.entity.Sale
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SaleRepository :
    JpaRepository<Sale, UUID>,
    JpaSpecificationExecutor<Sale> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Sale?

    /** 고객별 매출(테넌트 격리). 고객 도메인(SPEC-007)에서 사용. */
    fun findByUserIdAndCustomerId(
        userId: UUID,
        customerId: UUID,
        pageable: Pageable,
    ): Page<Sale>

    /** 비고 자동완성: 빈도(사용 횟수) 내림차순. */
    @Query(
        "SELECT s.note FROM Sale s " +
            "WHERE s.userId = :userId AND s.note IS NOT NULL AND s.note <> '' " +
            "GROUP BY s.note ORDER BY COUNT(s.note) DESC",
    )
    fun findNotesByFrequency(
        @Param("userId") userId: UUID,
    ): List<String>
}
