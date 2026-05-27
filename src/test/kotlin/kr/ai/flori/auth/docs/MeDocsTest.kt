package kr.ai.flori.auth.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get

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
}
