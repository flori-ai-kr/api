package kr.ai.flori.common.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * 접근 로그 인터셉터의 동작을 순수 단위 테스트로 검증(DB·컨텍스트 불필요).
 * - preHandle이 시작 시각을 기록하고, afterCompletion이 그 시각을 소비해도 예외가 없어야 한다.
 * - local/운영 프로필 분기 양쪽, 2xx/4xx/5xx 상태 모두 NPE·예외 없이 처리되어야 한다.
 */
class LoggingInterceptorTest {
    private fun interceptor(vararg profiles: String): LoggingInterceptor =
        LoggingInterceptor(MockEnvironment().apply { setActiveProfiles(*profiles) })

    @Test
    fun `preHandle은 시작 시각을 요청 속성에 기록하고 true를 반환한다`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val proceed = interceptor("local").preHandle(request, response, Any())

        assertThat(proceed).isTrue()
        assertThat(request.getAttribute("kr.ai.flori.requestStartTime")).isInstanceOf(java.lang.Long::class.java)
    }

    @Test
    fun `local 프로필에서 2xx 요청을 예외 없이 로깅한다`() {
        runWholeCycle(interceptor("local"), method = "GET", uri = "/sales", status = 200)
    }

    @Test
    fun `local 프로필에서 4xx 요청을 예외 없이 로깅한다`() {
        runWholeCycle(interceptor("local"), method = "POST", uri = "/sales", status = 400)
    }

    @Test
    fun `운영 프로필에서 5xx 요청을 구조화 필드로 예외 없이 로깅한다`() {
        runWholeCycle(interceptor("prod"), method = "GET", uri = "/sales", status = 500)
    }

    @Test
    fun `운영 프로필에서 2xx 요청을 구조화 필드로 예외 없이 로깅한다`() {
        runWholeCycle(interceptor("prod"), method = "GET", uri = "/health", status = 200)
    }

    @Test
    fun `프로필이 비어 있으면 local 동작으로 폴백한다`() {
        runWholeCycle(interceptor(), method = "GET", uri = "/sales", status = 200)
    }

    @Test
    fun `시작 시각이 없어도 afterCompletion은 예외 없이 동작한다`() {
        val request = MockHttpServletRequest("GET", "/sales")
        val response = MockHttpServletResponse().apply { status = 200 }
        // preHandle 없이 바로 호출 → startTime 속성 부재 경로
        interceptor("local").afterCompletion(request, response, Any(), null)
    }

    private fun runWholeCycle(
        interceptor: LoggingInterceptor,
        method: String,
        uri: String,
        status: Int,
    ) {
        val request = MockHttpServletRequest(method, uri)
        val response = MockHttpServletResponse().apply { this.status = status }
        interceptor.preHandle(request, response, Any())
        interceptor.afterCompletion(request, response, Any(), null)
    }
}
