package kr.ai.flori.auth.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
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
 *
 * 제공자 일반화: [SocialOAuthClient]를 구현해 빈 이름 "KAKAO"로 AuthService의 Map 주입에 참여한다.
 * 기존 [KakaoOAuthClient] 계약도 유지(이메일 미수집 MVP 동작 회귀 방지).
 */
@Component("KAKAO")
class KakaoOAuthClientImpl(
    builder: RestClient.Builder,
    private val properties: KakaoProperties,
) : KakaoOAuthClient,
    SocialOAuthClient {
    private val client = builder.build()

    /** 제공자 일반화 진입점. 카카오 동의항목의 이메일을 그대로 전달, provider="KAKAO". state 미사용. */
    override fun authenticate(
        code: String,
        redirectUri: String,
        state: String?,
    ): SocialUserInfo {
        val info = authenticate(code, redirectUri)
        return SocialUserInfo(
            provider = "KAKAO",
            providerId = info.providerId,
            email = info.email,
            nickname = info.nickname,
        )
    }

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
                throw AppException(CommonErrorCode.INVALID_TOKEN, "카카오 인증코드 교환에 실패했습니다", e)
            }
        return token?.accessToken
            ?: throw AppException(CommonErrorCode.INVALID_TOKEN, "카카오 토큰 응답이 비어 있습니다")
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
                throw AppException(CommonErrorCode.INVALID_TOKEN, "카카오 프로필 조회에 실패했습니다", e)
            }
        return me?.let {
            KakaoUserInfo(
                providerId = it.id.toString(),
                nickname = it.properties?.get("nickname"),
                email = it.kakaoAccount?.email,
            )
        } ?: throw AppException(CommonErrorCode.INVALID_TOKEN, "카카오 프로필 응답이 비어 있습니다")
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
    @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount? = null,
)

private data class KakaoAccount(
    val email: String? = null,
)
