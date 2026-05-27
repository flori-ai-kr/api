package kr.ai.flori.auth.oauth

import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class GoogleOAuthClientImplTest {
    @Test
    fun `인증코드로 토큰 교환 후 프로필을 반환한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withSuccess("""{"access_token":"google-at"}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
            .andRespond(
                withSuccess(
                    """{"sub":"google-sub-1","email":"shop@gmail.com","name":"구글 사장님"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val client = GoogleOAuthClientImpl(builder, GoogleProperties("client-id", "secret"))
        val info = client.authenticate("auth-code", "https://flori.kr/auth/callback/google", null)

        assertThat(info.provider).isEqualTo("GOOGLE")
        assertThat(info.providerId).isEqualTo("google-sub-1")
        assertThat(info.email).isEqualTo("shop@gmail.com")
        assertThat(info.nickname).isEqualTo("구글 사장님")
        server.verify()
    }

    @Test
    fun `구글이 4xx를 반환하면 AppException으로 변환한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val client = GoogleOAuthClientImpl(builder, GoogleProperties("client-id", "secret"))

        assertThatThrownBy { client.authenticate("bad-code", "https://flori.kr/auth/callback/google", null) }
            .isInstanceOf(AppException::class.java)
    }
}
