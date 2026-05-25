package com.hazel.common.error

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 표준 에러 응답 바디. 내부 디테일(스택/쿼리)은 절대 노출하지 않는다.
 */
@Schema(description = "표준 에러 응답. 모든 4xx/5xx는 이 형태를 반환한다.")
data class ErrorResponse(
    @field:Schema(description = "에러 코드(ErrorCode enum 이름)", example = "VALIDATION")
    val code: String,
    @field:Schema(description = "사람이 읽는 에러 메시지(내부 디테일 비노출)", example = "입력값이 올바르지 않습니다")
    val message: String,
)
