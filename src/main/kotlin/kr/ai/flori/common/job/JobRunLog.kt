package kr.ai.flori.common.job

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 백그라운드 작업 실행 이력 1건(append-only). 의미있는 실행만 기록(처리>0 OR 실패 OR 수동실행).
 * 0건 정상 실행은 여기에 안 남고 job_run_status만 갱신한다(노이즈 방지).
 */
@Entity
@Table(name = "job_run_logs")
class JobRunLog(
    @Column(name = "job_name", nullable = false)
    var jobName: String,
    @Column(name = "status", nullable = false)
    var status: String,
    @Column(name = "started_at", nullable = false)
    var startedAt: Instant,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "trigger", nullable = false)
    var trigger: String = "schedule"

    @Column(name = "processed_count", nullable = false)
    var processedCount: Int = 0

    @Column(name = "duration_ms")
    var durationMs: Int? = null

    @Column(name = "error_message")
    var errorMessage: String? = null

    @Column(name = "actor_user_id")
    var actorUserId: Long? = null

    @Column(name = "finished_at")
    var finishedAt: Instant? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    var metadata: JsonNode = JsonNodeFactory.instance.objectNode()
}
