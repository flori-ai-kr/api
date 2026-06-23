package kr.ai.flori.admin.repository

import kr.ai.flori.admin.entity.NotificationSendLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationSendLogRepository : JpaRepository<NotificationSendLog, Long> {
    @Query(
        "SELECT n FROM NotificationSendLog n " +
            "WHERE (:type IS NULL OR n.type = :type) " +
            "AND (:source IS NULL OR n.source = :source) " +
            "AND (:status IS NULL OR n.status = :status) " +
            "ORDER BY n.createdAt DESC",
    )
    fun search(
        @Param("type") type: String?,
        @Param("source") source: String?,
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<NotificationSendLog>
}
