package kr.ai.flori.common.log

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청마다 짧은 추적 ID(traceId)를 MDC에 넣어, 한 요청에서 발생한 모든 로그를 묶을 수 있게 한다.
 *
 * - 들어온 X-Request-Id 헤더가 있으면 그대로(정규화 후) 쓰고, 없으면 UUID 앞 8자리를 생성한다.
 * - 응답 헤더에도 X-Request-Id를 실어 클라이언트/프록시가 동일 ID로 상호 추적할 수 있게 한다.
 * - finally에서 반드시 MDC를 비운다(스레드풀 재사용 시 이전 요청 ID 누수 방지).
 *
 * 순서: HIGHEST_PRECEDENCE — JWT/보안 필터보다 **먼저** 돌아야 그 필터들의 로그에도 traceId가 찍힌다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = resolveTraceId(request)
        MDC.put(TRACE_ID_KEY, traceId)
        response.setHeader(REQUEST_ID_HEADER, traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_KEY)
        }
    }

    private fun resolveTraceId(request: HttpServletRequest): String {
        val incoming = request.getHeader(REQUEST_ID_HEADER)?.trim()
        if (!incoming.isNullOrEmpty()) {
            return incoming.take(MAX_TRACE_ID_LENGTH)
        }
        return UUID.randomUUID().toString().substring(0, GENERATED_TRACE_ID_LENGTH)
    }

    private companion object {
        const val TRACE_ID_KEY = "traceId"
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val GENERATED_TRACE_ID_LENGTH = 8
        const val MAX_TRACE_ID_LENGTH = 64
    }
}
