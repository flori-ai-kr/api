package com.hazel.common.error

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 표준 에러 응답 변환. (SPEC-004에서 Discord 웹훅 리포팅·핸들러 추가 예정)
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(AppException::class)
    fun handleApp(ex: AppException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(ex.errorCode.status)
            .body(ErrorResponse(ex.errorCode.name, ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .status(ErrorCode.VALIDATION.status)
            .body(ErrorResponse(ErrorCode.VALIDATION.name, detail ?: ErrorCode.VALIDATION.defaultMessage))
    }
}
