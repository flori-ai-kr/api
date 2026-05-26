package kr.ai.flori.auth.docs

import kr.ai.flori.auth.oauth.KakaoOAuthClient
import kr.ai.flori.auth.oauth.KakaoUserInfo
import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.post

/**
 * 카카오 OAuth 로그인 RestDocs 문서화. 실제 카카오 호출 대신 KakaoOAuthClient를 스텁 빈으로 대체한다.
 */
@Import(AuthOAuthDocsTest.StubKakaoConfig::class)
class AuthOAuthDocsTest : RestDocsSupport() {
    @TestConfiguration
    class StubKakaoConfig {
        @Bean
        @Primary
        fun stubKakaoClient(): KakaoOAuthClient =
            object : KakaoOAuthClient {
                override fun authenticate(
                    code: String,
                    redirectUri: String,
                ): KakaoUserInfo = KakaoUserInfo(providerId = "kakao-doc-123", nickname = "카카오 사장님")
            }
    }

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
                        responseFields =
                            listOf(
                                fieldWithPath("accessToken").type(JsonFieldType.STRING).description("access 토큰"),
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("refresh 토큰"),
                                fieldWithPath("expiresIn").type(JsonFieldType.NUMBER).description("access 만료까지 남은 초"),
                                fieldWithPath("tokenType").type(JsonFieldType.STRING).description("토큰 타입(Bearer)"),
                            ),
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
}
