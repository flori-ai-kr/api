package com.hazel.common.error

import org.springframework.http.HttpStatus

/**
 * 표준 에러 코드. SPEC-004에서 Discord 리포팅·핸들러 확장 예정.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val defaultMessage: String,
) {
    VALIDATION(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다"),
    DUPLICATE(HttpStatus.CONFLICT, "이미 존재합니다"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),
}
