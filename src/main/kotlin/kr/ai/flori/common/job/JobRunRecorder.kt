package kr.ai.flori.common.job

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/**
 * 백그라운드 작업 실행을 기록하는 공통 래퍼. cron 본문을 record()로 감싸면
 * 시작/종료/소요시간·상태·처리 건수를 자동 기록한다.
 *
 * - job_run_status: 매 실행 upsert(0건 정상 포함) — "마지막으로 언제 돌았나" 추적.
 * - job_run_logs:   의미있는 실행만 append(처리>0 OR 실패 OR 수동실행) — 노이즈 방지.
 *
 * 기록 자체가 작업을 막지 않도록 기록부 예외는 비차단 처리(로깅만).
 */
@Component
class JobRunRecorder(
    private val logRepository: JobRunLogRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("TooGenericExceptionCaught") // 작업 본문이 던지는 임의 예외를 포착해 failed로 기록
    fun record(
        jobName: String,
        trigger: String = TRIGGER_SCHEDULE,
        actorUserId: Long? = null,
        block: () -> JobOutcome,
    ): JobOutcome {
        val started = Instant.now()
        val outcome =
            try {
                block()
            } catch (e: Exception) {
                log.error("작업 실패 job={}", jobName, e)
                val failed = JobOutcome(JobStatus.FAILED)
                persist(jobName, failed, trigger, actorUserId, started, Instant.now(), e.message)
                return failed
            }
        persist(jobName, outcome, trigger, actorUserId, started, Instant.now(), null)
        return outcome
    }

    @Suppress("LongParameterList")
    private fun persist(
        jobName: String,
        outcome: JobOutcome,
        trigger: String,
        actorUserId: Long?,
        started: Instant,
        finished: Instant,
        errorMessage: String?,
    ) {
        val durationMs = Duration.between(started, finished).toMillis().toInt()
        runCatching { upsertStatus(jobName, outcome, started, finished, durationMs, errorMessage) }
            .onFailure { log.error("job_run_status 기록 실패 job={}", jobName, it) }

        val meaningful = outcome.status == JobStatus.FAILED || outcome.processedCount > 0 || trigger == TRIGGER_MANUAL
        if (meaningful) {
            runCatching { insertLog(jobName, outcome, trigger, actorUserId, started, finished, durationMs, errorMessage) }
                .onFailure { log.error("job_run_logs 기록 실패 job={}", jobName, it) }
        }
    }

    @Suppress("LongParameterList")
    private fun upsertStatus(
        jobName: String,
        outcome: JobOutcome,
        started: Instant,
        finished: Instant,
        durationMs: Int,
        errorMessage: String?,
    ) {
        val successAt = if (outcome.status == JobStatus.SUCCESS) Timestamp.from(finished) else null
        jdbcTemplate.update(
            "INSERT INTO job_run_status (job_name, last_status, last_run_at, last_finished_at, " +
                "last_duration_ms, last_processed_count, last_error_message, last_success_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                "ON CONFLICT (job_name) DO UPDATE SET " +
                "last_status = EXCLUDED.last_status, last_run_at = EXCLUDED.last_run_at, " +
                "last_finished_at = EXCLUDED.last_finished_at, last_duration_ms = EXCLUDED.last_duration_ms, " +
                "last_processed_count = EXCLUDED.last_processed_count, last_error_message = EXCLUDED.last_error_message, " +
                "last_success_at = COALESCE(EXCLUDED.last_success_at, job_run_status.last_success_at), updated_at = NOW()",
            jobName,
            outcome.status.value,
            Timestamp.from(started),
            Timestamp.from(finished),
            durationMs,
            outcome.processedCount,
            errorMessage,
            successAt,
        )
    }

    @Suppress("LongParameterList")
    private fun insertLog(
        jobName: String,
        outcome: JobOutcome,
        trigger: String,
        actorUserId: Long?,
        started: Instant,
        finished: Instant,
        durationMs: Int,
        errorMessage: String?,
    ) {
        val entry =
            JobRunLog(jobName = jobName, status = outcome.status.value, startedAt = started).apply {
                this.trigger = trigger
                this.processedCount = outcome.processedCount
                this.durationMs = durationMs
                this.errorMessage = errorMessage
                this.actorUserId = actorUserId
                this.finishedAt = finished
                this.metadata = objectMapper.valueToTree(outcome.metadata)
            }
        logRepository.save(entry)
    }

    private companion object {
        const val TRIGGER_SCHEDULE = "schedule"
        const val TRIGGER_MANUAL = "manual"
    }
}
