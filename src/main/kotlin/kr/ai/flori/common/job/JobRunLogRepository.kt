package kr.ai.flori.common.job

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface JobRunLogRepository : JpaRepository<JobRunLog, Long> {
    @Query(
        "SELECT j FROM JobRunLog j " +
            "WHERE (:jobName IS NULL OR j.jobName = :jobName) " +
            "AND (:status IS NULL OR j.status = :status) " +
            "ORDER BY j.createdAt DESC",
    )
    fun search(
        @Param("jobName") jobName: String?,
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<JobRunLog>
}
