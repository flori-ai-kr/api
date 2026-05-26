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
 * 카카오 OAuth 실연동: 인증코드 → 토큰 교환 → 프로필 조회.
 * RestClient.Builder를 주입받아(테스트에서 MockRestServiceServer 바인딩 가능) 절대 URL로 호출한다.
 * 카카오 4xx/5xx·네트워크 오류는 RestClientException → AppException(INVALID_TOKEN)으로 변환(원인 체이닝, 500 노출 방지).
 * client_secret은 '사용함'일 때만 전송한다('사용 안 함'이면 빈 값 → 생략).
 */
@Component
class KakaoOAuthClientImpl(
    builder: RestClient.Builder,
    private val properties: KakaoProperties,
) : KakaoOAuthClient {
    private val client = builder.build()

    override fun authenticate(
        code: String,
        redirectUri: String,
    ): KakaoUserInfo = fetchProfile(exchangeToken(code, redirectUri))

    private fun exchangeToken(
        code: String,
        redirectUri: String,
    ): String {
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", properties.restApiKey)
                if (properties.clientSecret.isNotBlank()) {
                    add("client_secret", properties.clientSecret)
                }
                add("redirect_uri", redirectUri)
                add("code", code)
            }
        val token =
            try {
                client
                    .post()
                    .uri("$KAUTH_BASE/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse::class.java)
            } catch (e: RestClientException) {
                throw AppException(ErrorCode.INVALID_TOKEN, "카카오 인증코드 교환에 실패했습니다", e)
            }
        return token?.accessToken
            ?: throw AppException(ErrorCode.INVALID_TOKEN, "카카오 토큰 응답이 비어 있습니다")
    }

    private fun fetchProfile(accessToken: String): KakaoUserInfo {
        val me =
            try {
                client
                    .get()
                    .uri("$KAPI_BASE/v2/user/me")
                    .header("Authorization", "Bearer $accessToken")
                    .retrieve()
                    .body(KakaoMeResponse::class.java)
            } catch (e: RestClientException) {
                throw AppException(ErrorCode.INVALID_TOKEN, "카카오 프로필 조회에 실패했습니다", e)
            }
        return me?.let {
            KakaoUserInfo(providerId = it.id.toString(), nickname = it.properties?.get("nickname"))
        } ?: throw AppException(ErrorCode.INVALID_TOKEN, "카카오 프로필 응답이 비어 있습니다")
    }

    private companion object {
        const val KAUTH_BASE = "https://kauth.kakao.com"
        const val KAPI_BASE = "https://kapi.kakao.com"
    }
}

private data class KakaoTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
)

private data class KakaoMeResponse(
    val id: Long,
    val properties: Map<String, String>? = null,
)
