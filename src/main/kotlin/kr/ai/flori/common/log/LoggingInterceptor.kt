package kr.ai.flori.common.log

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * HTTP 접근 로그(access log) 인터셉터 — 요청당 한 줄.
 *
 * 역할 분리(중요):
 * - 이 인터셉터는 **접근 라인만** 남긴다(method/uri/status/duration). 스택트레이스·바디·헤더는 절대 남기지 않는다.
 * - 5xx의 ERROR 로깅·스택·Discord 알림은 GlobalExceptionHandler가 이미 담당하므로
 *   여기서는 5xx를 **WARN(짧게)** 로만 남겨 중복 ERROR 로그를 만들지 않는다.
 * - 4xx는 WARN, 2xx/3xx는 INFO.
 *
 * 프로필 분기:
 * - local: 사람이 읽는 텍스트 한 줄 (`OK GET /sales 200 12ms`).
 * - 그 외(운영): logback이 JSON으로 직렬화하도록 kv로 구조화 필드를 넘긴다
 *   (http_method/request_uri/http_status/duration_ms).
 */
@Component
class LoggingInterceptor(
    environment: Environment,
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)
    private val isLocal =
        environment.activeProfiles.isEmpty() || environment.activeProfiles.contains(LOCAL_PROFILE)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val start = request.getAttribute(START_TIME_ATTR) as? Long ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - start
        val status = response.status
        val method = request.method
        val uri = request.requestURI

        if (isLocal) {
            logText(status, method, uri, duration)
        } else {
            logStructured(status, method, uri, duration)
        }
    }

    private fun logText(
        status: Int,
        method: String,
        uri: String,
        duration: Long,
    ) {
        if (status >= CLIENT_ERROR_THRESHOLD) {
            log.warn("$FAIL_ICON {} {} {} {}ms", method, uri, status, duration)
        } else {
            log.info("$OK_ICON {} {} {} {}ms", method, uri, status, duration)
        }
    }

    private fun logStructured(
        status: Int,
        method: String,
        uri: String,
        duration: Long,
    ) {
        val args =
            arrayOf(
                kv("http_method", method),
                kv("request_uri", uri),
                kv("http_status", status),
                kv("duration_ms", duration),
            )
        if (status >= CLIENT_ERROR_THRESHOLD) {
            log.warn("$FAIL_ICON request failed", *args)
        } else {
            log.info("$OK_ICON request completed", *args)
        }
    }

    private companion object {
        const val START_TIME_ATTR = "kr.ai.flori.requestStartTime"
        const val LOCAL_PROFILE = "local"
        const val CLIENT_ERROR_THRESHOLD = 400
        const val OK_ICON = "✅"
        const val FAIL_ICON = "❌"
    }
}
