package kr.ai.flori.storage.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class StorageDocsTest : RestDocsSupport() {
    @Test
    fun `스토리지 사용량 조회 문서화`() {
        val token = signupAndToken()
        mockMvc
            .get("/storage/usage") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "storage-usage-get",
                        responseSchema = "StorageUsageResponse",
                        tag = "Storage",
                        summary = "갤러리 스토리지 사용량 조회 (used/quota/percent/status)",
                        responseFields =
                            listOf(
                                fieldWithPath("usedBytes").type(JsonFieldType.NUMBER).description("사용 중 바이트"),
                                fieldWithPath("quotaBytes").type(JsonFieldType.NUMBER).description("한도 바이트(기본 3GiB)"),
                                fieldWithPath("percent").type(JsonFieldType.NUMBER).description("사용률(%)"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("OK | WARN(>=90%) | FULL(>=100%)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `스토리지 증설 요청 문서화`() {
        val token = signupAndToken()
        mockMvc
            .post("/storage/increase-request") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("reason" to "사진이 많아 용량이 부족합니다"))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "storage-increase-request-create",
                        requestSchema = "StorageIncreaseRequestCreate",
                        responseSchema = "StorageRequestResponse",
                        tag = "Storage",
                        summary = "스토리지 증설 요청(관리자에게 Discord 알림) — 90%+ 도달 시 노출",
                        requestFields =
                            listOf(
                                fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("증설 사유(선택)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("요청 ID"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("PENDING | APPROVED | REJECTED"),
                                fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("사유"),
                                fieldWithPath("resolvedBytes").type(JsonFieldType.NUMBER).optional().description("승인 시 상향된 한도"),
                                fieldWithPath("rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("요청 시각"),
                            ),
                    ),
                )
            }
    }
}
