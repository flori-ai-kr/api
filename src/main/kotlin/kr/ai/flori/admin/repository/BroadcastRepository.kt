package kr.ai.flori.admin.repository

import kr.ai.flori.admin.entity.Broadcast
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BroadcastRepository : JpaRepository<Broadcast, Long> {
    @Query(
        "SELECT b FROM Broadcast b " +
            "WHERE (:status IS NULL OR b.status = :status) " +
            "ORDER BY b.createdAt DESC",
    )
    fun search(
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<Broadcast>
}
