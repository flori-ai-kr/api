package kr.ai.flori.admin.dto

/** AI 헬스 타깃 1건. 상태/지연만 노출(키·내부 host 비노출). */
data class AiHealthTarget(
    val name: String,
    val status: String,
    val latencyMs: Long?,
    val detail: String?,
)

data class AiHealthResponse(
    val targets: List<AiHealthTarget>,
)
