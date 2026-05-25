package kr.ai.flori.common.error

/**
 * 표준 에러 응답 바디. 내부 디테일(스택/쿼리)은 절대 노출하지 않는다.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
)
