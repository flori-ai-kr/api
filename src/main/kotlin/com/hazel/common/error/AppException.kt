package com.hazel.common.error

/**
 * 애플리케이션 도메인 예외. 컨트롤러 어드바이스가 표준 응답으로 변환한다.
 */
class AppException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.defaultMessage,
) : RuntimeException(message)
