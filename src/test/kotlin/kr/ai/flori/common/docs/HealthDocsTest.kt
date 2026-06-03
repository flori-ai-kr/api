package kr.ai.flori.common.docs

import org.junit.jupiter.api.Test
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get

/**
 * HealthController RestDocs 문서화.
 * 공개 엔드포인트 — 인증 헤더 불필요. DB/인증에 의존하지 않는 경량 헬스체크.
 */
class HealthDocsTest : RestDocsSupport() {
    @Test
    fun `헬스체크 문서화`() {
        mockMvc
            .get("/health")
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "health-check",
                        responseSchema = "HealthResponse",
                        tag = "Health",
                        summary = "헬스체크 (서버 가용 여부 반환, 인증 불필요)",
                        responseFields =
                            listOf(
                                fieldWithPath("status")
                                    .type(JsonFieldType.STRING)
                                    .description("서버 상태. 정상이면 \"UP\""),
                                fieldWithPath("service")
                                    .type(JsonFieldType.STRING)
                                    .description("서비스 식별자 (flori-ai-server)"),
                                fieldWithPath("time")
                                    .type(JsonFieldType.STRING)
                                    .description("현재 서버 시각 (ISO-8601 오프셋 형식)"),
                            ),
                    ),
                )
            }
    }
}
