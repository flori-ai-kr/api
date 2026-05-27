package kr.ai.flori.user.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.post

/**
 * 온보딩 API RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 POST /me/onboarding을 1회 호출하며 OpenAPI를 생성한다.
 */
class OnboardingDocsTest : RestDocsSupport() {
    @Test
    fun `온보딩 제출 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/me/onboarding") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "헤이즐 플라워",
                            "regionSido" to "서울특별시",
                            "regionSigungu" to "강남구",
                            "ownerAgeRange" to "30대",
                            "interests" to listOf("웨딩", "개업화환"),
                            "specialties" to listOf("꽃다발", "화분"),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "me-onboarding",
                        tag = "Me",
                        summary = "온보딩 제출(가게 프로필 저장)",
                        requestFields =
                            listOf(
                                fieldWithPath("name").type(JsonFieldType.STRING).description("가게명(필수)"),
                                fieldWithPath("regionSido").type(JsonFieldType.STRING).description("시/도(필수, 웹 enum 값)"),
                                fieldWithPath("regionSigungu").type(JsonFieldType.STRING).optional().description("시군구(선택)"),
                                fieldWithPath("ownerAgeRange").type(JsonFieldType.STRING).optional().description("나이대(선택, 단일)"),
                                fieldWithPath("interests").type(JsonFieldType.ARRAY).optional().description("관심사(선택, 복수)"),
                                fieldWithPath("specialties").type(JsonFieldType.ARRAY).optional().description("가게 주력(선택, 복수)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).optional().description("로그인 이메일"),
                                fieldWithPath("name").type(JsonFieldType.STRING).optional().description("계정 표시 이름"),
                                fieldWithPath("onboarded").type(JsonFieldType.BOOLEAN).description("온보딩 완료 여부(제출 후 true)"),
                                fieldWithPath("profile").type(JsonFieldType.OBJECT).description("저장된 가게 프로필"),
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
