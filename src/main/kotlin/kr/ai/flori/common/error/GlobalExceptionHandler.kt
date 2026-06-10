package kr.ai.flori.common.error

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

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
    fun handleApp(ex: AppException): ResponseEntity<ErrorResponse> {
        val status = ex.errorCode.status
        when {
            // 5xx(예상된 내부오류) → ERROR + 스택
            status.is5xxServerError -> log.error("[{}] {} - {}", status.value(), ex.errorCode.code, ex.message, ex)
            // 4xx인데 원인(cause)이 있으면(외부연동 실패 등) cause 체인까지 로깅 — 예: OAuth 토큰 교환 실패
            ex.cause != null -> log.warn("[{}] {} - {}", status.value(), ex.errorCode.code, ex.message, ex)
            // 그 외 일반 4xx → 한 줄 WARN
            else -> log.warn("[{}] {} - {}", status.value(), ex.errorCode.code, ex.message)
        }
        return ResponseEntity
            .status(status)
            .body(ErrorResponse(ex.errorCode.code, ex.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
        log.warn("검증 실패: {}", detail)
        return errorResponse(CommonErrorCode.VALIDATION, detail)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraint(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> =
        errorResponse(CommonErrorCode.VALIDATION, ex.constraintViolations.firstOrNull()?.message)

    // 본문 파싱 실패(필수 필드 누락·타입 불일치·깨진 JSON)는 클라이언트 오류 → 400 (내부 디테일 비노출, Discord 미전송)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(): ResponseEntity<ErrorResponse> = errorResponse(CommonErrorCode.VALIDATION, "요청 본문을 해석할 수 없습니다")

    // 필수 쿼리/요청 파라미터 누락 → 400 (클라이언트 오류, Discord 미전송)
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
        log.warn("필수 파라미터 누락: {}", ex.parameterName)
        return errorResponse(CommonErrorCode.VALIDATION, "필수 파라미터가 누락되었습니다: ${ex.parameterName}")
    }

    // 쿼리/경로 파라미터 타입 불일치(예: 날짜 형식 오류) → 400 (클라이언트 오류, Discord 미전송)
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        log.warn("파라미터 타입 불일치: {}", ex.name)
        return errorResponse(CommonErrorCode.VALIDATION, "파라미터 형식이 올바르지 않습니다: ${ex.name}")
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(): ResponseEntity<ErrorResponse> {
        // JDBC 제약위반 메시지에는 위반 값(전화번호·이메일 등 PII)이 포함될 수 있어 로깅하지 않는다.
        log.warn("데이터 제약 위반 (상세 미기록 — PII 보호)")
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
