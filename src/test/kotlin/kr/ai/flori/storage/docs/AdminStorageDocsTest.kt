package kr.ai.flori.storage.docs

import kr.ai.flori.common.docs.RestDocsSupport
import kr.ai.flori.storage.entity.StorageIncreaseRequest
import kr.ai.flori.storage.repository.StorageIncreaseRequestRepository
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
import org.springframework.test.web.servlet.post

class AdminStorageDocsTest : RestDocsSupport() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var storageIncreaseRequestRepository: StorageIncreaseRequestRepository

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }

    private fun createOwnerUserId(): Long {
        val tokens = TestAccounts.register(authService, tokenProvider)
        return tokenProvider.parse(tokens.accessToken)!!.userId
    }

    @Test
    fun `관리자 증설 요청 목록 문서화`() {
        val token = adminToken()
        val ownerId = createOwnerUserId()
        storageIncreaseRequestRepository.save(
            StorageIncreaseRequest(userId = ownerId, reason = "사진이 많아요"),
        )

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
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("요청 ID"),
                                fieldWithPath("[].userId").type(JsonFieldType.NUMBER).description("요청 유저"),
                                fieldWithPath("[].nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                                fieldWithPath("[].storeName").type(JsonFieldType.STRING).optional().description("가게명"),
                                fieldWithPath("[].reason").type(JsonFieldType.STRING).optional().description("사유"),
                                fieldWithPath("[].status").type(JsonFieldType.STRING).description("PENDING | APPROVED | REJECTED"),
                                fieldWithPath("[].rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
                                fieldWithPath("[].resolvedBytes").type(JsonFieldType.NUMBER).optional().description("승인 시 한도"),
                                fieldWithPath("[].usedBytes").type(JsonFieldType.NUMBER).description("현재 사용량"),
                                fieldWithPath("[].quotaBytes").type(JsonFieldType.NUMBER).description("현재 한도"),
                                fieldWithPath("[].createdAt").type(JsonFieldType.STRING).description("요청 시각"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `관리자 증설 승인 문서화`() {
        val token = adminToken()
        val ownerId = createOwnerUserId()
        val req =
            storageIncreaseRequestRepository.save(
                StorageIncreaseRequest(userId = ownerId, reason = "사진이 많아요"),
            )

        mockMvc
            .post("/admin/storage/requests/${req.id}/approve") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/admin/storage/requests/{id}/approve",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("quotaBytes" to 5L * 1024 * 1024 * 1024))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-storage-request-approve",
                        requestSchema = "ApproveRequest",
                        responseSchema = "AdminStorageRequestResponse",
                        tag = "AdminStorage",
                        summary = "증설 요청 승인 — quota 상향 + 점주 푸시",
                        pathParameters = listOf(parameterWithName("id").description("요청 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("quotaBytes").type(JsonFieldType.NUMBER).description("새 한도(바이트)"),
                            ),
                        responseFields = adminStorageRequestFields(),
                    ),
                )
            }
    }

    @Test
    fun `관리자 증설 거절 문서화`() {
        val token = adminToken()
        val ownerId = createOwnerUserId()
        val req =
            storageIncreaseRequestRepository.save(
                StorageIncreaseRequest(userId = ownerId, reason = "용량 부족"),
            )

        mockMvc
            .post("/admin/storage/requests/${req.id}/reject") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/admin/storage/requests/{id}/reject",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("reason" to "현재 사용량이 적습니다"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-storage-request-reject",
                        requestSchema = "RejectRequest",
                        responseSchema = "AdminStorageRequestResponse",
                        tag = "AdminStorage",
                        summary = "증설 요청 거절 — 사유 기록 + 점주 푸시",
                        pathParameters = listOf(parameterWithName("id").description("요청 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("reason").type(JsonFieldType.STRING).description("거절 사유"),
                            ),
                        responseFields = adminStorageRequestFields(),
                    ),
                )
            }
    }

    private fun adminStorageRequestFields() =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("요청 ID"),
            fieldWithPath("userId").type(JsonFieldType.NUMBER).description("요청 유저"),
            fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
            fieldWithPath("storeName").type(JsonFieldType.STRING).optional().description("가게명"),
            fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("사유"),
            fieldWithPath("status").type(JsonFieldType.STRING).description("PENDING | APPROVED | REJECTED"),
            fieldWithPath("rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
            fieldWithPath("resolvedBytes").type(JsonFieldType.NUMBER).optional().description("승인 시 한도"),
            fieldWithPath("usedBytes").type(JsonFieldType.NUMBER).description("현재 사용량"),
            fieldWithPath("quotaBytes").type(JsonFieldType.NUMBER).description("현재 한도"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("요청 시각"),
        )
}
