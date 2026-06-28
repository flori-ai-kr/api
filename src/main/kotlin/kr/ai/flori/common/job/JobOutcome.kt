package kr.ai.flori.common.job

enum class JobStatus(
    val value: String,
) {
    SUCCESS("success"),
    FAILED("failed"),
    SKIPPED("skipped"),
}

/**
 * 작업 본문 실행 결과. cron 본문(runXxx)이 반환하며 JobRunRecorder가 기록한다.
 * - SUCCESS: 정상 완료(processedCount=처리 건수, 0건도 정상)
 * - SKIPPED: no-op(키 미설정 등) — 실행 안 함
 * - FAILED: 본문에서 명시적 실패(대개는 예외로 처리되어 Recorder가 자동 변환)
 */
data class JobOutcome(
    val status: JobStatus,
    val processedCount: Int = 0,
    val metadata: Map<String, Any?> = emptyMap(),
) {
    companion object {
        fun success(
            processedCount: Int,
            metadata: Map<String, Any?> = emptyMap(),
        ) = JobOutcome(JobStatus.SUCCESS, processedCount, metadata)

        fun skipped() = JobOutcome(JobStatus.SKIPPED)
    }
}
