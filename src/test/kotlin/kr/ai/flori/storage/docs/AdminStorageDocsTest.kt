package kr.ai.flori.storage.docs

import kr.ai.flori.common.docs.RestDocsSupport
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch

class AdminStorageDocsTest : RestDocsSupport() {
    @Autowired
    private lateinit var userRepository: UserRepository

    /** 가입 후 isAdmin=true 로 승격한 운영자 토큰. */
    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }

    /** 평범한 신규 점주를 만들고 userId 반환(quota 상향 대상용). */
    private fun createOwnerUserId(): Long {
        val tokens = TestAccounts.register(authService, tokenProvider)
        return tokenProvider.parse(tokens.accessToken)!!.userId
    }

    @Test
    fun `관리자 증설 요청 목록 문서화`() {
        val token = adminToken()
        mockMvc
            .get("/admin/storage/requests?status=PENDING") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-storage-requests-list",
                        responseSchema = "AdminStorageRequestListResponse",
                        tag = "AdminStorage",
                        summary = "스토리지 증설 요청 목록(상태 필터)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("요청 목록"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `관리자 quota 상향 문서화`() {
        val token = adminToken()
        val targetUserId = createOwnerUserId()
        mockMvc
            .patch("/admin/storage/users/$targetUserId/quota") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/admin/storage/users/{userId}/quota",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("quotaBytes" to 5L * 1024 * 1024 * 1024))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-storage-quota-update",
                        requestSchema = "QuotaUpdateRequest",
                        responseSchema = "StorageUsageResponse",
                        tag = "AdminStorage",
                        summary = "유저 스토리지 한도 상향(증설) — 해당 유저 PENDING 요청 자동 RESOLVED",
                        pathParameters = listOf(parameterWithName("userId").description("대상 유저 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("quotaBytes").type(JsonFieldType.NUMBER).description("새 한도(바이트)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("usedBytes").type(JsonFieldType.NUMBER).description("사용량"),
                                fieldWithPath("quotaBytes").type(JsonFieldType.NUMBER).description("상향된 한도"),
                                fieldWithPath("percent").type(JsonFieldType.NUMBER).description("사용률"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("OK|WARN|FULL"),
                            ),
                    ),
                )
            }
    }
}
