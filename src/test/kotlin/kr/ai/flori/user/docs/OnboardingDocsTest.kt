package kr.ai.flori.user.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.post

/**
 * 가게 프로필 편집 API RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 POST /me/profile을 1회 호출하며 OpenAPI를 생성한다.
 */
class OnboardingDocsTest : RestDocsSupport() {
    @Test
    fun `가게 프로필 편집 문서화`() {
        val token = signupAndToken()
        // 닉네임은 전역 유일(uq_users_nickname) — 공유 컨텍스트의 다른 문서 테스트와 충돌하지 않도록 고유 값 사용
        val nickname = "헤이즐-${java.util.UUID.randomUUID()}"

        mockMvc
            .post("/me/profile") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "헤이즐 플라워",
                            "regionSido" to "서울특별시",
                            "nickname" to nickname,
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
                        identifier = "me-profile-update",
                        requestSchema = "OnboardingRequest",
                        responseSchema = "UserResponse",
                        tag = "Me",
                        summary = "가게 프로필 편집(upsert)",
                        requestFields =
                            listOf(
                                fieldWithPath("name").type(JsonFieldType.STRING).description("가게명(필수)"),
                                fieldWithPath("regionSido").type(JsonFieldType.STRING).description("시/도(필수, 웹 enum 값)"),
                                fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임(선택, 전역유일)"),
                                fieldWithPath("regionSigungu").type(JsonFieldType.STRING).optional().description("시군구(선택)"),
                                fieldWithPath("ownerAgeRange").type(JsonFieldType.STRING).optional().description("나이대(선택, 단일)"),
                                fieldWithPath("interests").type(JsonFieldType.ARRAY).optional().description("관심사(선택, 복수)"),
                                fieldWithPath("specialties").type(JsonFieldType.ARRAY).optional().description("가게 주력(선택, 복수)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).optional().description("로그인 이메일"),
                                fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                                fieldWithPath("profile").type(JsonFieldType.OBJECT).description("저장된 가게 프로필"),
                                fieldWithPath("profile.storeName").type(JsonFieldType.STRING).description("가게명"),
                                fieldWithPath("profile.regionSido").type(JsonFieldType.STRING).description("시/도"),
                                fieldWithPath("profile.regionSigungu").type(JsonFieldType.STRING).optional().description("시군구(미설정 시 null)"),
                                fieldWithPath("profile.ownerAgeRange").type(JsonFieldType.STRING).optional().description("나이대(미설정 시 null)"),
                                fieldWithPath("profile.interests").type(JsonFieldType.ARRAY).description("관심사 목록"),
                                fieldWithPath("profile.specialties").type(JsonFieldType.ARRAY).description("가게 주력 목록"),
                                fieldWithPath(
                                    "profile.profileImageUrl",
                                ).type(JsonFieldType.STRING).optional().description("프로필 이미지 URL(미설정 시 null)"),
                                fieldWithPath("profile.tourCompleted").type(JsonFieldType.BOOLEAN).description("인앱 투어 완료 여부"),
                            ),
                    ),
                )
            }
    }
}
