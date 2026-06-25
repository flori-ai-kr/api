package kr.ai.flori.admin.dto

import java.time.Instant

/** 작업별 최신 상태(job_run_status 1행). 한번도 안 돈 작업은 last* 가 null. */
data class JobRunSummaryResponse(
    val jobName: String,
    val lastStatus: String?,
    val lastRunAt: Instant?,
    val lastFinishedAt: Instant?,
    val lastDurationMs: Int?,
    val lastProcessedCount: Int,
    val lastErrorMessage: String?,
    val lastSuccessAt: Instant?,
)

/** 작업 실행 이력 1건(job_run_logs). */
data class JobRunLogResponse(
    val id: Long,
    val jobName: String,
    val status: String,
    val trigger: String,
    val processedCount: Int,
    val durationMs: Int?,
    val errorMessage: String?,
    val actorUserId: Long?,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val createdAt: Instant,
)
