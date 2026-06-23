package kr.ai.flori.admin.repository

import kr.ai.flori.admin.entity.AdminAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, Long> {
    @Query(
        "SELECT a FROM AdminAuditLog a " +
            "WHERE (:action IS NULL OR a.action = :action) " +
            "AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId) " +
            "ORDER BY a.createdAt DESC",
    )
    fun search(
        @Param("action") action: String?,
        @Param("actorUserId") actorUserId: Long?,
        pageable: Pageable,
    ): Page<AdminAuditLog>
}
