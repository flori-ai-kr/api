package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.JobRunLogResponse
import kr.ai.flori.admin.dto.JobRunSummaryResponse
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.job.JobNames
import kr.ai.flori.common.job.JobRunLog
import kr.ai.flori.common.job.JobRunLogRepository
import kr.ai.flori.common.job.JobRunRecorder
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 백그라운드 작업(cron) 실행 로그 조회 + 수동 트리거.
 * @RequiresAdmin 하위에서만 호출(cross-tenant 운영 도구).
 */
@Service
class AdminJobRunService(
    private val jobRunLogRepository: JobRunLogRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val registry: JobRegistry,
    private val jobRunRecorder: JobRunRecorder,
    private val audit: AdminAuditService,
) {
    /** 6개 작업의 최신 상태(JobNames.ALL 순서). 한번도 안 돈 작업은 last* null. */
    @Transactional(readOnly = true)
    fun summary(): List<JobRunSummaryResponse> {
        val byName =
            jdbcTemplate
                .query("SELECT * FROM job_run_status") { rs, _ -> rs.getString("job_name") to mapStatus(rs) }
                .toMap()
        return JobNames.ALL.map { name -> byName[name] ?: emptyStatus(name) }
    }

    @Transactional(readOnly = true)
    fun list(
        jobName: String?,
        status: String?,
        page: Int,
        size: Int,
    ): List<JobRunLogResponse> =
        jobRunLogRepository
            .search(
                jobName?.takeIf { it.isNotBlank() },
                status?.takeIf { it.isNotBlank() },
                Paging.pageSize(page, size, MAX_PAGE_SIZE),
            ).content
            .map { it.toResponse() }

    /**
     * 작업을 즉시 1회 실행한다(동기). 실행은 record()로 감싸 이력(manual)에 남고,
     * 트리거 행위는 감사 로그에 기록된다. 미등록 작업은 404.
     * @return 실행 직후 최신 상태.
     */
    fun trigger(jobName: String): JobRunSummaryResponse {
        val body = registry.body(jobName) ?: throw AppException(CommonErrorCode.NOT_FOUND)
        val actorUserId = TenantContext.currentUserId()
        audit.record(
            action = "JOB_TRIGGER",
            targetType = "job",
            targetId = jobName,
            summary = "$jobName 수동 실행",
        )
        jobRunRecorder.record(jobName, trigger = "manual", actorUserId = actorUserId) { body() }
        return currentStatus(jobName)
    }

    private fun currentStatus(jobName: String): JobRunSummaryResponse =
        jdbcTemplate
            .query("SELECT * FROM job_run_status WHERE job_name = ?", { rs, _ -> mapStatus(rs) }, jobName)
            .firstOrNull() ?: emptyStatus(jobName)

    private fun mapStatus(rs: ResultSet) =
        JobRunSummaryResponse(
            jobName = rs.getString("job_name"),
            lastStatus = rs.getString("last_status"),
            lastRunAt = rs.getTimestamp("last_run_at")?.toInstant(),
            lastFinishedAt = rs.getTimestamp("last_finished_at")?.toInstant(),
            lastDurationMs = (rs.getObject("last_duration_ms") as? Number)?.toInt(),
            lastProcessedCount = rs.getInt("last_processed_count"),
            lastErrorMessage = rs.getString("last_error_message"),
            lastSuccessAt = rs.getTimestamp("last_success_at")?.toInstant(),
        )

    private fun emptyStatus(jobName: String) =
        JobRunSummaryResponse(
            jobName = jobName,
            lastStatus = null,
            lastRunAt = null,
            lastFinishedAt = null,
            lastDurationMs = null,
            lastProcessedCount = 0,
            lastErrorMessage = null,
            lastSuccessAt = null,
        )

    private fun JobRunLog.toResponse() =
        JobRunLogResponse(
            id = id!!,
            jobName = jobName,
            status = status,
            trigger = trigger,
            processedCount = processedCount,
            durationMs = durationMs,
            errorMessage = errorMessage,
            actorUserId = actorUserId,
            startedAt = startedAt,
            finishedAt = finishedAt,
            createdAt = createdAt,
        )

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
