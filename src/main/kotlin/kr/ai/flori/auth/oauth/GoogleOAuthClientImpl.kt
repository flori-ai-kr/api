package kr.ai.flori.auth.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * 구글 OAuth 실연동: 인증코드 → 토큰 교환 → OIDC userinfo 조회.
 * RestClient.Builder를 주입받아(테스트에서 MockRestServiceServer 바인딩 가능) 절대 URL로 호출한다.
 * 구글 4xx/5xx·네트워크 오류는 RestClientException → AppException(INVALID_TOKEN)으로 변환(원인 체이닝, 500 노출 방지).
 *
 * 빈 이름 "GOOGLE"로 AuthService의 Map 주입에 참여한다. provider="GOOGLE", providerId=sub.
 */
@Component("GOOGLE")
class GoogleOAuthClientImpl(
    builder: RestClient.Builder,
    private val properties: GoogleProperties,
) : SocialOAuthClient {
    private val client = builder.build()

    override fun authenticate(
        code: String,
        redirectUri: String,
        state: String?,
    ): SocialUserInfo = fetchProfile(exchangeToken(code, redirectUri))

    private fun exchangeToken(
        code: String,
        redirectUri: String,
    ): String {
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", properties.clientId)
                add("client_secret", properties.clientSecret)
                add("redirect_uri", redirectUri)
                add("code", code)
            }
        val token =
            try {
                client
                    .post()
                    .uri("$OAUTH_BASE/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse::class.java)
            } catch (e: RestClientException) {
                throw AppException(ErrorCode.INVALID_TOKEN, "구글 인증코드 교환에 실패했습니다", e)
            }
        return token?.accessToken
            ?: throw AppException(ErrorCode.INVALID_TOKEN, "구글 토큰 응답이 비어 있습니다")
    }

    private fun fetchProfile(accessToken: String): SocialUserInfo {
        val me =
            try {
                client
                    .get()
                    .uri(USERINFO_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .retrieve()
                    .body(GoogleUserInfoResponse::class.java)
            } catch (e: RestClientException) {
                throw AppException(ErrorCode.INVALID_TOKEN, "구글 프로필 조회에 실패했습니다", e)
            }
        return me?.let {
            SocialUserInfo(
                provider = "GOOGLE",
                providerId = it.sub,
                email = it.email,
                nickname = it.name,
            )
        } ?: throw AppException(ErrorCode.INVALID_TOKEN, "구글 프로필 응답이 비어 있습니다")
    }

    private companion object {
        const val OAUTH_BASE = "https://oauth2.googleapis.com"
        const val USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
    }
}

private data class GoogleTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
)

private data class GoogleUserInfoResponse(
    val sub: String,
    val email: String? = null,
    val name: String? = null,
)
