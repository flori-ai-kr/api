package kr.ai.flori.common.error

import org.springframework.http.HttpStatus

/**
 * 횡단(공통) 에러 코드. 특정 도메인에 속하지 않고 인프라(JWT 필터, 검증, 제네릭 핸들러)에서 쓰인다.
 *
 * 도메인 전용 의미가 필요하면 `<domain>/error`에 별도 enum을 정의한다(예: auth/error/AuthErrorCode).
 * 여기 `CONFLICT`는 제약 위반 등 의미가 특정되지 않은 일반 409 폴백이다.
 */
enum class CommonErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    VALIDATION("E-CMN-001", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    UNAUTHORIZED("E-CMN-002", HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    INVALID_TOKEN("E-CMN-003", HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    FORBIDDEN("E-CMN-004", HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND("E-CMN-005", HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다"),
    CONFLICT("E-CMN-006", HttpStatus.CONFLICT, "이미 존재합니다"),
    TOO_MANY_REQUESTS("E-CMN-007", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요"),
    INTERNAL("E-CMN-999", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),
}
