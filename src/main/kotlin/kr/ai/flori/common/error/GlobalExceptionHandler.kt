package kr.ai.flori.common.error

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

/**
 * 표준 에러 응답 변환 + 예기치 못한 오류의 Discord 리포팅.
 *
 * - 예상된 예외(AppException, 검증, 제약위반)는 그대로 매핑(Discord 전송 안 함).
 * - 예기치 못한 예외(5xx)만 Discord 리포팅 + 일반 메시지로 교체(내부 디테일 비노출).
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val reporter: DiscordErrorReporter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AppException::class)
    fun handleApp(ex: AppException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(ex.errorCode.status)
            .body(ErrorResponse(ex.errorCode.code, ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
        return errorResponse(CommonErrorCode.VALIDATION, detail)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraint(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> =
        errorResponse(CommonErrorCode.VALIDATION, ex.constraintViolations.firstOrNull()?.message)

    // 본문 파싱 실패(필수 필드 누락·타입 불일치·깨진 JSON)는 클라이언트 오류 → 400 (내부 디테일 비노출, Discord 미전송)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(): ResponseEntity<ErrorResponse> = errorResponse(CommonErrorCode.VALIDATION, "요청 본문을 해석할 수 없습니다")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("데이터 제약 위반: {}", ex.mostSpecificCause.message)
        return errorResponse(CommonErrorCode.CONFLICT, null)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(): ResponseEntity<ErrorResponse> = errorResponse(CommonErrorCode.FORBIDDEN, null)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        reporter.report(ex, mapOf("action" to request.getDescription(false)))
        return errorResponse(CommonErrorCode.INTERNAL, null)
    }

    private fun errorResponse(
        code: ErrorCode,
        detail: String?,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(code.status)
            .body(ErrorResponse(code.code, detail ?: code.defaultMessage))
}
