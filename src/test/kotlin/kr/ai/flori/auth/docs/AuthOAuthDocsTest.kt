package kr.ai.flori.auth.docs

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
 * 실제 제공자 호출 대신 SocialOAuthClient 스텁 빈(KAKAO/GOOGLE/NAVER)으로 실제 구현 빈을 오버라이드한다.
 */
@Import(AuthOAuthDocsTest.StubSocialConfig::class)
@TestPropertySource(properties = ["spring.main.allow-bean-definition-overriding=true"])
class AuthOAuthDocsTest : RestDocsSupport() {
    @TestConfiguration
    class StubSocialConfig {
        // 빈 이름을 제공자 키와 일치시켜 AuthService의 Map<String, SocialOAuthClient> 주입에 그대로 들어가게 한다.
        @Bean("KAKAO")
        fun stubKakaoClient(): SocialOAuthClient = stub("KAKAO", email = null, nickname = "카카오 사장님")

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

    private val tokenResponseFields =
        listOf(
            fieldWithPath("accessToken").type(JsonFieldType.STRING).description("access 토큰"),
            fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("refresh 토큰"),
            fieldWithPath("expiresIn").type(JsonFieldType.NUMBER).description("access 만료까지 남은 초"),
            fieldWithPath("tokenType").type(JsonFieldType.STRING).description("토큰 타입(Bearer)"),
        )

    @Test
    fun `카카오 로그인 문서화 — 신규 생성 후 동일 신원 재로그인`() {
        val body = json(mapOf("code" to "auth-code", "redirectUri" to "flori://oauth/kakao"))

        mockMvc
            .post("/auth/oauth/kakao") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-oauth-kakao",
                        tag = "Auth",
                        summary = "카카오 로그인(인증코드 교환)",
                        requestFields =
                            listOf(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("카카오 authorization code"),
                                fieldWithPath("redirectUri").type(JsonFieldType.STRING).description("앱에서 사용한 redirect URI"),
                            ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }

        // 동일 신원 재로그인 → 생성 없이 200 (find-existing 경로 커버)
        mockMvc
            .post("/auth/oauth/kakao") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `구글 로그인 문서화 — 신규 생성 후 동일 신원 재로그인`() {
        val body = json(mapOf("code" to "auth-code", "redirectUri" to "https://flori.kr/auth/callback/google"))

        mockMvc
            .post("/auth/oauth/google") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-oauth-google",
                        tag = "Auth",
                        summary = "구글 로그인(인증코드 교환)",
                        requestFields =
                            listOf(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("구글 authorization code"),
                                fieldWithPath("redirectUri").type(JsonFieldType.STRING).description("앱에서 사용한 redirect URI"),
                            ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }

        mockMvc
            .post("/auth/oauth/google") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `네이버 로그인 문서화 — 신규 생성 후 동일 신원 재로그인`() {
        val body =
            json(
                mapOf(
                    "code" to "auth-code",
                    "redirectUri" to "https://flori.kr/auth/callback/naver",
                    "state" to "state-123",
                ),
            )

        mockMvc
            .post("/auth/oauth/naver") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-oauth-naver",
                        tag = "Auth",
                        summary = "네이버 로그인(인증코드 교환)",
                        requestFields =
                            listOf(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("네이버 authorization code"),
                                fieldWithPath("redirectUri").type(JsonFieldType.STRING).description("앱에서 사용한 redirect URI"),
                                fieldWithPath("state").type(JsonFieldType.STRING).description("CSRF 방지용 state"),
                            ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }

        mockMvc
            .post("/auth/oauth/naver") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
    }
}
