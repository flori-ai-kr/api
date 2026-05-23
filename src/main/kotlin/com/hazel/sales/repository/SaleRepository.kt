package com.hazel.sales.repository

import com.hazel.sales.entity.Sale
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
