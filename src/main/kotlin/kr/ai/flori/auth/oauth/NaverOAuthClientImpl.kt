package kr.ai.flori.auth.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

/**
 * 네이버 OAuth 실연동: 인증코드 → 토큰 교환 → 회원 프로필 조회.
 * RestClient.Builder를 주입받아(테스트에서 MockRestServiceServer 바인딩 가능) 절대 URL로 호출한다.
 * 네이버 4xx/5xx·네트워크 오류는 RestClientException → AppException(INVALID_TOKEN)으로 변환(원인 체이닝, 500 노출 방지).
 *
 * 빈 이름 "NAVER"로 AuthService의 Map 주입에 참여한다. provider="NAVER", providerId=response.id.
 * 네이버 토큰 교환은 state가 필수다(없으면 INVALID_TOKEN).
 */
@Component("NAVER")
class NaverOAuthClientImpl(
    builder: RestClient.Builder,
    private val properties: NaverProperties,
) : SocialOAuthClient {
    private val client = builder.build()

    override fun authenticate(
        code: String,
        redirectUri: String,
        state: String?,
    ): SocialUserInfo {
        val resolvedState =
            state?.takeIf { it.isNotBlank() }
                ?: throw AppException(CommonErrorCode.INVALID_TOKEN, "네이버 로그인에는 state가 필요합니다")
        return fetchProfile(exchangeToken(code, resolvedState))
    }

    private fun exchangeToken(
        code: String,
        state: String,
    ): String {
        // 네이버 토큰 엔드포인트는 쿼리 파라미터로 받는다.
        val uri =
            UriComponentsBuilder
                .fromUriString("$NID_BASE/oauth2.0/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", properties.clientId)
                .queryParam("client_secret", properties.clientSecret)
                .queryParam("code", code)
                .queryParam("state", state)
                .build()
                .toUriString()
        val token =
            try {
                client
                    .post()
                    .uri(uri)
                    .retrieve()
                    .body(NaverTokenResponse::class.java)
            } catch (e: RestClientException) {
                throw AppException(CommonErrorCode.INVALID_TOKEN, "네이버 인증코드 교환에 실패했습니다", e)
            }
        return token?.accessToken
            ?: throw AppException(CommonErrorCode.INVALID_TOKEN, "네이버 토큰 응답이 비어 있습니다")
    }

    private fun fetchProfile(accessToken: String): SocialUserInfo {
        val me =
            try {
                client
                    .get()
                    .uri("$OPENAPI_BASE/v1/nid/me")
                    .header("Authorization", "Bearer $accessToken")
                    .retrieve()
                    .body(NaverMeResponse::class.java)
            } catch (e: RestClientException) {
                throw AppException(CommonErrorCode.INVALID_TOKEN, "네이버 프로필 조회에 실패했습니다", e)
            }
        val profile =
            me?.response
                ?: throw AppException(CommonErrorCode.INVALID_TOKEN, "네이버 프로필 응답이 비어 있습니다")
        return SocialUserInfo(
            provider = "NAVER",
            providerId = profile.id,
            email = profile.email,
            nickname = profile.name ?: profile.nickname,
        )
    }

    private companion object {
        const val NID_BASE = "https://nid.naver.com"
        const val OPENAPI_BASE = "https://openapi.naver.com"
    }
}

private data class NaverTokenResponse(
    @JsonProperty("access_token") val accessToken: String? = null,
)

private data class NaverMeResponse(
    val response: NaverProfile? = null,
)

private data class NaverProfile(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val nickname: String? = null,
)
