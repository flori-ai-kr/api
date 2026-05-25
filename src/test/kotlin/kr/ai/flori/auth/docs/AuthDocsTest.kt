package kr.ai.flori.auth.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Auth API RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI를 생성한다.
 */
class AuthDocsTest : RestDocsSupport() {
    private fun email() = "auth-doc-${UUID.randomUUID()}@flori.dev"

    private val tokenResponseFields =
        listOf(
            fieldWithPath("accessToken").type(JsonFieldType.STRING).description("API 호출용 access 토큰(짧은 TTL)"),
            fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("access 재발급용 refresh 토큰(로테이션)"),
            fieldWithPath("expiresIn").type(JsonFieldType.NUMBER).description("access 만료까지 남은 초"),
            fieldWithPath("tokenType").type(JsonFieldType.STRING).description("토큰 타입(Bearer)"),
        )

    private fun signup(em: String): Pair<String, String> {
        val res =
            mockMvc
                .post("/auth/signup") {
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("email" to em, "password" to "password123"))
                }.andReturn()
                .response.contentAsString
        val j = objectMapper.readTree(res)
        return j.get("accessToken").asText() to j.get("refreshToken").asText()
    }

    @Test
    fun `signup 문서화`() {
        mockMvc
            .post("/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("email" to email(), "password" to "password123", "name" to "사장님"))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-signup",
                        tag = "Auth",
                        summary = "회원가입",
                        requestFields =
                            listOf(
                                fieldWithPath("email").type(JsonFieldType.STRING).description("로그인 이메일"),
                                fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호(8~72자)"),
                                fieldWithPath("name").type(JsonFieldType.STRING).optional().description("표시 이름(선택)"),
                            ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }
    }

    @Test
    fun `login 문서화`() {
        val em = email()
        signup(em)
        mockMvc
            .post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("email" to em, "password" to "password123"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-login",
                        tag = "Auth",
                        summary = "로그인",
                        requestFields =
                            listOf(
                                fieldWithPath("email").type(JsonFieldType.STRING).description("로그인 이메일"),
                                fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호"),
                            ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }
    }

    @Test
    fun `refresh 문서화`() {
        val (_, refresh) = signup(email())
        mockMvc
            .post("/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("refreshToken" to refresh))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-refresh",
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
        val (_, refresh) = signup(email())
        mockMvc
            .post("/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("refreshToken" to refresh))
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-logout",
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
