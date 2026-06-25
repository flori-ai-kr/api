package kr.ai.flori.common.job

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class JobRunRecorderTest {
    @Autowired
    lateinit var recorder: JobRunRecorder

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private fun statusOf(job: String) = jdbcTemplate.queryForMap("SELECT * FROM job_run_status WHERE job_name = ?", job)

    private fun logCount(job: String) =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_run_logs WHERE job_name = ?", Int::class.java, job) ?: 0

    @Test
    fun `성공(처리 건수 있음)은 status upsert와 log를 모두 남긴다`() {
        recorder.record("test_succ_n") { JobOutcome.success(5) }

        val status = statusOf("test_succ_n")
        assertThat(status["last_status"]).isEqualTo("success")
        assertThat(status["last_processed_count"]).isEqualTo(5)
        assertThat(status["last_success_at"]).isNotNull()
        assertThat(logCount("test_succ_n")).isEqualTo(1)
    }

    @Test
    fun `성공(0건)은 status만 갱신하고 log는 남기지 않는다`() {
        recorder.record("test_succ_zero") { JobOutcome.success(0) }

        val status = statusOf("test_succ_zero")
        assertThat(status["last_status"]).isEqualTo("success")
        assertThat(status["last_processed_count"]).isEqualTo(0)
        assertThat(logCount("test_succ_zero")).isEqualTo(0)
    }

    @Test
    fun `skipped는 status만 갱신하고 log는 남기지 않는다`() {
        recorder.record("test_skip") { JobOutcome.skipped() }

        assertThat(statusOf("test_skip")["last_status"]).isEqualTo("skipped")
        assertThat(logCount("test_skip")).isEqualTo(0)
    }

    @Test
    fun `본문 예외는 failed status와 log를 남긴다`() {
        val outcome = recorder.record("test_fail") { error("boom") }

        assertThat(outcome.status).isEqualTo(JobStatus.FAILED)
        val status = statusOf("test_fail")
        assertThat(status["last_status"]).isEqualTo("failed")
        assertThat(status["last_error_message"]).isEqualTo("boom")
        assertThat(logCount("test_fail")).isEqualTo(1)
    }

    @Test
    fun `수동 실행은 0건이어도 log를 남긴다`() {
        recorder.record("test_manual", trigger = "manual", actorUserId = 1L) { JobOutcome.success(0) }

        assertThat(logCount("test_manual")).isEqualTo(1)
        val logRow =
            jdbcTemplate.queryForMap("SELECT * FROM job_run_logs WHERE job_name = ?", "test_manual")
        assertThat(logRow["trigger"]).isEqualTo("manual")
        assertThat(logRow["actor_user_id"]).isEqualTo(1L)
    }

    @Test
    fun `반복 실행 시 status는 마지막 값으로 갱신된다`() {
        recorder.record("test_repeat") { JobOutcome.success(3) }
        recorder.record("test_repeat") { JobOutcome.success(0) }

        val status = statusOf("test_repeat")
        assertThat(status["last_processed_count"]).isEqualTo(0)
        assertThat(status["last_status"]).isEqualTo("success")
        // 첫 실행(3건)만 의미있어 log는 1건, 둘째(0건)는 status만 갱신
        assertThat(logCount("test_repeat")).isEqualTo(1)
        // 0건 성공도 성공이므로 마지막 성공 시각은 유지/갱신된다
        assertThat(status["last_success_at"]).isNotNull()
    }
}
