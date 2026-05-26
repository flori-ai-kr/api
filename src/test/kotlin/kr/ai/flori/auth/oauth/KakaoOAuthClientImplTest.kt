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

class KakaoOAuthClientImplTest {
    @Test
    fun `인증코드로 토큰 교환 후 프로필을 반환한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withSuccess("""{"access_token":"kakao-at"}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andRespond(
                withSuccess(
                    """{"id":424242,"properties":{"nickname":"헤이즐 플라워"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val client = KakaoOAuthClientImpl(builder, KakaoProperties("rest-key", "secret"))
        val info = client.authenticate("auth-code", "flori://oauth/kakao")

        assertThat(info.providerId).isEqualTo("424242")
        assertThat(info.nickname).isEqualTo("헤이즐 플라워")
        server.verify()
    }

    @Test
    fun `카카오가 4xx를 반환하면 AppException으로 변환한다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val client = KakaoOAuthClientImpl(builder, KakaoProperties("rest-key", ""))

        assertThatThrownBy { client.authenticate("bad-code", "flori://oauth/kakao") }
            .isInstanceOf(AppException::class.java)
    }
}
