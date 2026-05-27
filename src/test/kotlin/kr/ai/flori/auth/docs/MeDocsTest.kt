package kr.ai.flori.auth.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.util.UUID

/**
 * Me API RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI를 생성한다.
 */
class MeDocsTest : RestDocsSupport() {
    @Test
    fun `me 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "me-get",
                        tag = "Me",
                        summary = "내 프로필 조회",
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("로그인 이메일"),
                                fieldWithPath("name").type(JsonFieldType.STRING).optional().description("표시 이름(미설정 시 null)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `me 이메일 보완 문서화`() {
        val token = signupAndToken()
        val newEmail = "updated-${UUID.randomUUID()}@flori.dev"

        mockMvc
            .patch("/me/email") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("email" to newEmail))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "me-email-update",
                        tag = "Me",
                        summary = "내 이메일 보완(소셜 가입 후)",
                        requestFields =
                            listOf(
                                fieldWithPath("email").type(JsonFieldType.STRING).description("설정할 이메일(형식 검증 + 중복 검사)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("보완된 이메일"),
                                fieldWithPath("name").type(JsonFieldType.STRING).optional().description("표시 이름(미설정 시 null)"),
                            ),
                    ),
                )
            }
    }
}
