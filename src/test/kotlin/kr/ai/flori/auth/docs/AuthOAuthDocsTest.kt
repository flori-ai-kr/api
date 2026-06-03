package kr.ai.flori.auth.docs

import kr.ai.flori.auth.oauth.AccessTokenOAuthClient
import kr.ai.flori.auth.oauth.SocialOAuthClient
import kr.ai.flori.auth.oauth.SocialUserInfo
import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.post

/**
 * 소셜 OAuth 로그인(카카오/구글/네이버) RestDocs 문서화.
 *
 * 실제 제공자 호출 대신 SocialOAuthClient 스텁 빈(KAKAO/GOOGLE/NAVER)으로 실제 구현 빈을 오버라이드한다.
 * 스텁은 고정 신원을 반환하지만, oauth 경로는 User를 생성하지 않으므로(가입은 register/complete에서만)
 * 매 호출이 항상 신규 신원으로 처리되어 OAuthResult(registered=false)를 문서화한다.
 */
@Import(AuthOAuthDocsTest.StubSocialConfig::class)
@TestPropertySource(properties = ["spring.main.allow-bean-definition-overriding=true"])
class AuthOAuthDocsTest : RestDocsSupport() {
    @TestConfiguration
    class StubSocialConfig {
        // 빈 이름을 제공자 키와 일치시켜 AuthService의 Map<String, SocialOAuthClient> 주입에 그대로 들어가게 한다.
        // 카카오는 웹(code) + 앱(accessToken) 두 경로를 지원하므로 두 인터페이스를 모두 구현한 스텁을 쓴다.
        @Bean("KAKAO")
        fun stubKakaoClient(): SocialOAuthClient =
            object : SocialOAuthClient, AccessTokenOAuthClient {
                private val info =
                    SocialUserInfo(
                        provider = "KAKAO",
                        providerId = "KAKAO-doc-123",
                        email = null,
                        nickname = "카카오 사장님",
                    )

                override fun authenticate(
                    code: String,
                    redirectUri: String,
                    state: String?,
                ): SocialUserInfo = info

                override fun authenticateWithAccessToken(accessToken: String): SocialUserInfo = info
            }

        @Bean("GOOGLE")
        fun stubGoogleClient(): SocialOAuthClient = stub("GOOGLE", email = "shop@gmail.com", nickname = "구글 사장님")

        @Bean("NAVER")
        fun stubNaverClient(): SocialOAuthClient = stub("NAVER", email = "shop@naver.com", nickname = "네이버 사장님")

        private fun stub(
            provider: String,
            email: String?,
            nickname: String?,
        ): SocialOAuthClient =
            object : SocialOAuthClient {
                override fun authenticate(
                    code: String,
                    redirectUri: String,
                    state: String?,
                ): SocialUserInfo =
                    SocialUserInfo(
                        provider = provider,
                        providerId = "$provider-doc-123",
                        email = email,
                        nickname = nickname,
                    )
            }
    }

    private val oauthResultFields =
        listOf(
            fieldWithPath("registered").type(JsonFieldType.BOOLEAN).description("기존 사용자 여부(true=로그인, false=가입 필요)"),
            fieldWithPath("token").type(JsonFieldType.OBJECT).optional().description("로그인 토큰(registered=true일 때만)"),
            fieldWithPath("registerToken").type(JsonFieldType.STRING).optional().description("가입 대기 토큰(registered=false, 5분 TTL)"),
            fieldWithPath("socialEmail").type(JsonFieldType.STRING).optional().description("소셜 이메일(온보딩 기본값, 없으면 null)"),
            fieldWithPath("socialNickname").type(JsonFieldType.STRING).optional().description("소셜 닉네임(온보딩 기본값, 없으면 null)"),
        )

    @Test
    fun `카카오 로그인 문서화 — 신규 신원이라 registerToken 반환`() {
        mockMvc
            .post("/auth/oauth/kakao") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("code" to "auth-code", "redirectUri" to "flori://oauth/kakao"))
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.registered") { value(false) } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-oauth-kakao",
                        requestSchema = "KakaoOAuthRequest",
                        responseSchema = "OAuthResult",
                        tag = "Auth",
                        summary = "카카오 로그인(웹 code 교환 / 앱 accessToken)",
                        requestFields =
                            listOf(
                                fieldWithPath("accessToken")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("앱 네이티브 SDK access token. 있으면 code 교환을 건너뛴다."),
                                fieldWithPath("code")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("웹 카카오 authorization code(accessToken 미지정 시 필수)"),
                                fieldWithPath("redirectUri")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("웹 code 교환 시 사용한 redirect URI"),
                            ),
                        responseFields = oauthResultFields,
                    ),
                )
            }
    }

    @Test
    fun `카카오 앱 로그인 — accessToken 경로로 신규 신원 처리`() {
        mockMvc
            .post("/auth/oauth/kakao") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("accessToken" to "kakao-access-token"))
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.registered") { value(false) } }
            .andExpect { jsonPath("$.registerToken") { exists() } }
    }

    @Test
    fun `카카오 로그인 — accessToken·code 모두 없으면 검증 실패(400)`() {
        mockMvc
            .post("/auth/oauth/kakao") {
                contentType = MediaType.APPLICATION_JSON
                content = json(emptyMap<String, String>())
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `구글 로그인 문서화 — 신규 신원이라 registerToken 반환`() {
        mockMvc
            .post("/auth/oauth/google") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("code" to "auth-code", "redirectUri" to "https://flori.kr/auth/callback/google"))
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.registered") { value(false) } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-oauth-google",
                        requestSchema = "GoogleOAuthRequest",
                        responseSchema = "OAuthResult",
                        tag = "Auth",
                        summary = "구글 로그인(인증코드 교환)",
                        requestFields =
                            listOf(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("구글 authorization code"),
                                fieldWithPath("redirectUri").type(JsonFieldType.STRING).description("앱에서 사용한 redirect URI"),
                            ),
                        responseFields = oauthResultFields,
                    ),
                )
            }
    }

    @Test
    fun `네이버 로그인 문서화 — 신규 신원이라 registerToken 반환`() {
        mockMvc
            .post("/auth/oauth/naver") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "code" to "auth-code",
                            "redirectUri" to "https://flori.kr/auth/callback/naver",
                            "state" to "state-123",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.registered") { value(false) } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-oauth-naver",
                        requestSchema = "NaverOAuthRequest",
                        responseSchema = "OAuthResult",
                        tag = "Auth",
                        summary = "네이버 로그인(인증코드 교환)",
                        requestFields =
                            listOf(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("네이버 authorization code"),
                                fieldWithPath("redirectUri").type(JsonFieldType.STRING).description("앱에서 사용한 redirect URI"),
                                fieldWithPath("state").type(JsonFieldType.STRING).description("CSRF 방지용 state"),
                            ),
                        responseFields = oauthResultFields,
                    ),
                )
            }
    }
}
