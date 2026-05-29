package kr.ai.flori.common.request

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// TraceIdFilter(HIGHEST_PRECEDENCE) 직후, 인증 필터보다 먼저 컨텍스트를 준비한다.
private const val CLIENT_CONTEXT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10

/**
 * 요청 헤더/원격주소에서 클라이언트 메타데이터를 추출해 [ClientContext]에 적재한다.
 * refresh 토큰 발급 시 통계용 컨텍스트(채널/기기/UA/IP)로 사용된다.
 *
 * - client_id: `X-Client-Id`, device_id: `X-Device-Id` (앱/웹이 전송하는 규약 헤더)
 * - user_agent: `User-Agent` 헤더
 * - ip: `X-Forwarded-For`의 최초 IP(프록시/로드밸런서 뒤) → 없으면 remoteAddr
 *
 * 각 값은 DB 컬럼 길이에 맞춰 절단한다. finally에서 반드시 clear(스레드풀 재사용 시 누수 방지).
 */
@Component
@Order(CLIENT_CONTEXT_FILTER_ORDER)
class ClientContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        ClientContext.set(
            ClientContext.Info(
                clientId = header(request, "X-Client-Id", MAX_CLIENT_ID),
                deviceId = header(request, "X-Device-Id", MAX_DEVICE_ID),
                userAgent = header(request, "User-Agent", MAX_USER_AGENT),
                ip = resolveIp(request),
            ),
        )
        try {
            filterChain.doFilter(request, response)
        } finally {
            ClientContext.clear()
        }
    }

    private fun header(
        request: HttpServletRequest,
        name: String,
        max: Int,
    ): String? =
        request
            .getHeader(name)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(max)

    /** 프록시 뒤에서는 X-Forwarded-For 선두 IP가 실제 클라이언트. 없으면 직접 연결 주소. */
    private fun resolveIp(request: HttpServletRequest): String? {
        val forwarded =
            request
                .getHeader("X-Forwarded-For")
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        return (forwarded ?: request.remoteAddr)?.take(MAX_IP)
    }

    private companion object {
        const val MAX_CLIENT_ID = 64
        const val MAX_DEVICE_ID = 128
        const val MAX_USER_AGENT = 512
        const val MAX_IP = 45
    }
}
