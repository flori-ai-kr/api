package kr.ai.flori.common.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * traceId MDC 필터를 순수 단위 테스트로 검증.
 * - 들어온 X-Request-Id가 있으면 그대로 사용하고, 없으면 생성한다.
 * - 응답 헤더에 traceId가 실린다.
 * - 처리 후 MDC가 비워진다(스레드풀 누수 방지).
 */
class TraceIdFilterTest {
    private val filter = TraceIdFilter()

    @Test
    fun `들어온 X-Request-Id 헤더를 traceId로 사용하고 응답 헤더에 싣는다`() {
        val request = MockHttpServletRequest("GET", "/sales").apply { addHeader("X-Request-Id", "abc12345") }
        val response = MockHttpServletResponse()
        val captured = CapturingFilterChain()

        filter.doFilter(request, response, captured)

        assertThat(captured.traceIdDuringChain).isEqualTo("abc12345")
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc12345")
        assertThat(MDC.get("traceId")).isNull() // 처리 후 정리됨
    }

    @Test
    fun `헤더가 없으면 traceId를 생성한다`() {
        val request = MockHttpServletRequest("GET", "/sales")
        val response = MockHttpServletResponse()
        val captured = CapturingFilterChain()

        filter.doFilter(request, response, captured)

        assertThat(captured.traceIdDuringChain).isNotBlank()
        assertThat(captured.traceIdDuringChain).hasSize(8)
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(captured.traceIdDuringChain)
        assertThat(MDC.get("traceId")).isNull()
    }

    @Test
    fun `공백 헤더는 무시하고 새 traceId를 생성한다`() {
        val request = MockHttpServletRequest("GET", "/sales").apply { addHeader("X-Request-Id", "   ") }
        val response = MockHttpServletResponse()
        val captured = CapturingFilterChain()

        filter.doFilter(request, response, captured)

        assertThat(captured.traceIdDuringChain).hasSize(8)
    }

    /** 체인이 도는 동안의 MDC traceId 값을 포착해, 다운스트림 로그가 ID를 가지는지 검증한다. */
    private class CapturingFilterChain : MockFilterChain() {
        var traceIdDuringChain: String? = null

        override fun doFilter(
            request: jakarta.servlet.ServletRequest,
            response: jakarta.servlet.ServletResponse,
        ) {
            traceIdDuringChain = MDC.get("traceId")
            super.doFilter(request, response)
        }
    }
}
