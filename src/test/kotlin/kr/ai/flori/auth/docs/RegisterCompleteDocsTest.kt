package kr.ai.flori.auth.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * 가입 완료(register/complete) RestDocs 문서화.
 * registerToken을 직접 발급해(소셜 신규 로그인 결과와 동일 형태) 가입을 완료하고 토큰 발급을 문서화한다.
 */
class RegisterCompleteDocsTest : RestDocsSupport() {
    @Test
    fun `가입 완료 문서화`() {
        val email = "register-doc-${UUID.randomUUID()}@flori.dev"
        val registerToken =
            tokenProvider.generateRegisterToken(
                provider = "KAKAO",
                providerId = "kakao-${UUID.randomUUID()}",
                email = email,
                nickname = "헤이즐",
            )

        mockMvc
            .post("/auth/register/complete") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "registerToken" to registerToken,
                            "storeName" to "헤이즐 플라워",
                            "nickname" to "헤이즐",
                            "email" to email,
                            "regionSido" to "서울특별시",
                            "regionSigungu" to "강남구",
                            "ownerAgeRange" to "30대",
                            "interests" to listOf("웨딩", "개업화환"),
                            "specialties" to listOf("꽃다발", "화분"),
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-register-complete",
                        requestSchema = "RegisterCompleteRequest",
                        responseSchema = "TokenResponse",
                        tag = "Auth",
                        summary = "가입 완료(온보딩) — registerToken으로 User+가게 프로필 생성 후 토큰 발급",
                        requestFields =
                            listOf(
                                fieldWithPath("registerToken").type(JsonFieldType.STRING).description("소셜 로그인이 반환한 registerToken(5분 TTL)"),
                                fieldWithPath("storeName").type(JsonFieldType.STRING).description("가게명(필수)"),
                                fieldWithPath("nickname").type(JsonFieldType.STRING).description("계정 표시명(기본값=소셜 닉네임, 필수)"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("로그인 이메일(기본값=소셜 이메일, 필수)"),
                                fieldWithPath("regionSido").type(JsonFieldType.STRING).description("시/도(필수, 웹 enum 값)"),
                                fieldWithPath("regionSigungu").type(JsonFieldType.STRING).optional().description("시군구(선택)"),
                                fieldWithPath("ownerAgeRange").type(JsonFieldType.STRING).optional().description("나이대(선택, 단일)"),
                                fieldWithPath("interests").type(JsonFieldType.ARRAY).optional().description("관심사(선택, 복수)"),
                                fieldWithPath("specialties").type(JsonFieldType.ARRAY).optional().description("가게 주력(선택, 복수)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("accessToken").type(JsonFieldType.STRING).description("API 호출용 access 토큰(짧은 TTL)"),
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("access 재발급용 refresh 토큰(로테이션)"),
                                fieldWithPath("expiresIn").type(JsonFieldType.NUMBER).description("access 만료까지 남은 초"),
                                fieldWithPath("tokenType").type(JsonFieldType.STRING).description("토큰 타입(Bearer)"),
                            ),
                    ),
                )
            }
    }
}
