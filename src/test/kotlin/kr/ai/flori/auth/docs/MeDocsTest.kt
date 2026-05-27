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
                                fieldWithPath("onboarded")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("온보딩 완료 여부(false면 앱이 온보딩 화면으로 라우팅)"),
                                fieldWithPath("profile")
                                    .type(JsonFieldType.OBJECT)
                                    .optional()
                                    .description("가게 프로필"),
                                fieldWithPath("profile.storeName").type(JsonFieldType.STRING).description("가게명"),
                                fieldWithPath("profile.regionSido").type(JsonFieldType.STRING).description("시/도"),
                                fieldWithPath("profile.regionSigungu").type(JsonFieldType.STRING).optional().description("시군구(미설정 시 null)"),
                                fieldWithPath("profile.ownerAgeRange").type(JsonFieldType.STRING).optional().description("나이대(미설정 시 null)"),
                                fieldWithPath("profile.interests").type(JsonFieldType.ARRAY).description("관심사 목록"),
                                fieldWithPath("profile.specialties").type(JsonFieldType.ARRAY).description("가게 주력 목록"),
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
                                fieldWithPath("onboarded").type(JsonFieldType.BOOLEAN).description("온보딩 완료 여부"),
                                fieldWithPath("profile")
                                    .type(JsonFieldType.OBJECT)
                                    .optional()
                                    .description("가게 프로필"),
                                fieldWithPath("profile.storeName").type(JsonFieldType.STRING).description("가게명"),
                                fieldWithPath("profile.regionSido").type(JsonFieldType.STRING).description("시/도"),
                                fieldWithPath("profile.regionSigungu").type(JsonFieldType.STRING).optional().description("시군구(미설정 시 null)"),
                                fieldWithPath("profile.ownerAgeRange").type(JsonFieldType.STRING).optional().description("나이대(미설정 시 null)"),
                                fieldWithPath("profile.interests").type(JsonFieldType.ARRAY).description("관심사 목록"),
                                fieldWithPath("profile.specialties").type(JsonFieldType.ARRAY).description("가게 주력 목록"),
                            ),
                    ),
                )
            }
    }
}
