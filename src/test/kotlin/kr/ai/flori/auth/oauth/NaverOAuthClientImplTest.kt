package kr.ai.flori.auth.oauth

import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class NaverOAuthClientImplTest {
    @Test
    fun `인증코드와 state로 토큰 교환 후 프로필을 반환한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        // 토큰 엔드포인트는 쿼리 파라미터로 받으므로 base 경로 포함 여부로 매칭한다.
        server
            .expect(requestTo(containsString("https://nid.naver.com/oauth2.0/token")))
            .andRespond(withSuccess("""{"access_token":"naver-at"}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andRespond(
                withSuccess(
                    """{"response":{"id":"naver-id-1","email":"shop@naver.com","name":"네이버 사장님"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val client = NaverOAuthClientImpl(builder, NaverProperties("client-id", "secret"))
        val info = client.authenticate("auth-code", "https://flori.kr/auth/callback/naver", "state-123")

        assertThat(info.provider).isEqualTo("NAVER")
        assertThat(info.providerId).isEqualTo("naver-id-1")
        assertThat(info.email).isEqualTo("shop@naver.com")
        assertThat(info.nickname).isEqualTo("네이버 사장님")
        server.verify()
    }

    @Test
    fun `네이버가 4xx를 반환하면 AppException으로 변환한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo(containsString("https://nid.naver.com/oauth2.0/token")))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val client = NaverOAuthClientImpl(builder, NaverProperties("client-id", "secret"))

        assertThatThrownBy { client.authenticate("bad-code", "https://flori.kr/auth/callback/naver", "state-123") }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `state가 없으면 AppException으로 변환한다`() {
        val builder = RestClient.builder()
        val client = NaverOAuthClientImpl(builder, NaverProperties("client-id", "secret"))

        assertThatThrownBy { client.authenticate("auth-code", "https://flori.kr/auth/callback/naver", null) }
            .isInstanceOf(AppException::class.java)
    }
}
