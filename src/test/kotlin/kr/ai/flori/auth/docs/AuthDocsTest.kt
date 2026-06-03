package kr.ai.flori.auth.docs

import kr.ai.flori.common.docs.RestDocsSupport
import kr.ai.flori.support.TestAccounts
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.post

/**
 * Auth(refresh/logout) RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI를 생성한다.
 * (가입/소셜 로그인 문서는 register/complete·oauth 문서 테스트가 담당한다.)
 */
class AuthDocsTest : RestDocsSupport() {
    private val tokenResponseFields =
        listOf(
            fieldWithPath("accessToken").type(JsonFieldType.STRING).description("API 호출용 access 토큰(짧은 TTL)"),
            fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("access 재발급용 refresh 토큰(로테이션)"),
            fieldWithPath("expiresIn").type(JsonFieldType.NUMBER).description("access 만료까지 남은 초"),
            fieldWithPath("tokenType").type(JsonFieldType.STRING).description("토큰 타입(Bearer)"),
        )

    /** 신규 소셜 가입을 완료하고 refresh 토큰을 반환한다. */
    private fun refreshToken(): String = TestAccounts.register(authService, tokenProvider).refreshToken

    @Test
    fun `refresh 문서화`() {
        mockMvc
            .post("/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("refreshToken" to refreshToken()))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-refresh",
                        requestSchema = "RefreshRequest",
                        responseSchema = "TokenResponse",
                        tag = "Auth",
                        summary = "토큰 갱신(refresh 로테이션)",
                        requestFields =
                            listOf(
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("발급받은 refresh 토큰"),
                            ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }
    }

    @Test
    fun `logout 문서화`() {
        mockMvc
            .post("/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("refreshToken" to refreshToken()))
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-logout",
                        requestSchema = "LogoutRequest",
                        tag = "Auth",
                        summary = "로그아웃(refresh 무효화)",
                        requestFields =
                            listOf(
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("무효화할 refresh 토큰"),
                            ),
                    ),
                )
            }
    }
}
