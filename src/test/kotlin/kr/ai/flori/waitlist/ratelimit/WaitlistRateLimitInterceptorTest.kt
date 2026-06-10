package kr.ai.flori.waitlist.ratelimit

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class WaitlistRateLimitInterceptorTest {
    private val res = MockHttpServletResponse()

    private fun post(ip: String) =
        MockHttpServletRequest().apply {
            method = "POST"
            remoteAddr = ip
        }

    @Test
    fun `POST 윈도 내 한도 초과 시 429(AppException) 발생`() {
        val interceptor = WaitlistRateLimitInterceptor(maxPerWindow = 3, windowSeconds = 600L)
        val req = post("1.1.1.1")

        repeat(3) { assertThat(interceptor.preHandle(req, res, Any())).isTrue() }

        assertThatThrownBy { interceptor.preHandle(req, res, Any()) }
            .isInstanceOf(AppException::class.java)
            .extracting("errorCode")
            .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS)
    }

    @Test
    fun `IP 별로 카운트가 분리된다`() {
        val interceptor = WaitlistRateLimitInterceptor(maxPerWindow = 2, windowSeconds = 600L)
        repeat(2) { interceptor.preHandle(post("1.1.1.1"), res, Any()) }

        // 다른 IP 는 영향 없음
        assertThat(interceptor.preHandle(post("2.2.2.2"), res, Any())).isTrue()
    }

    @Test
    fun `GET 은 제한하지 않는다`() {
        val interceptor = WaitlistRateLimitInterceptor(maxPerWindow = 1, windowSeconds = 600L)
        val req =
            MockHttpServletRequest().apply {
                method = "GET"
                remoteAddr = "1.1.1.1"
            }

        repeat(10) { assertThat(interceptor.preHandle(req, res, Any())).isTrue() }
    }
}
