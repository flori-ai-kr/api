package kr.ai.flori.admin.docs

import kr.ai.flori.admin.dto.AiHealthResponse
import kr.ai.flori.admin.dto.AiHealthTarget
import kr.ai.flori.admin.service.AiHealthService
import kr.ai.flori.common.docs.RestDocsSupport
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.entity.BusinessVerification
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 운영 콘솔(/admin 하위 전체) RestDocs 문서화. 모든 엔드포인트는 @RequiresAdmin(User.isAdmin 재검증) 게이팅이라
 * 운영자 토큰으로 호출한다(cross-tenant). AI 헬스는 외부 ping이 비결정적이므로 [AiHealthService]를 스텁한다.
 */
class AdminDocsTest : RestDocsSupport() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var aiHealthService: AiHealthService

    /** 가입 후 isAdmin=true 로 승격한 운영자 토큰. */
    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }

    /** 평범한 신규 유저를 만들고 userId 반환(승격 대상·상세 대상용). */
    private fun registerUser(): Long {
        val tokens = TestAccounts.register(authService, tokenProvider)
        return tokenProvider.parse(tokens.accessToken)!!.userId
    }

    private fun pendingVerification(userId: Long): Long =
        businessVerificationRepository
            .save(
                BusinessVerification(
                    userId = userId,
                    businessNumber = "1234567890",
                    businessName = "플로리",
                    representativeName = "홍길동",
                    businessLicenseUrl = "https://cdn.example.com/business-licenses/$userId/a.jpg",
                ),
            ).id!!

    private val verificationFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("인증 신청 ID"),
            fieldWithPath("userId").type(JsonFieldType.NUMBER).description("신청 유저 ID"),
            fieldWithPath("businessNumber").type(JsonFieldType.STRING).description("사업자등록번호"),
            fieldWithPath("businessName").type(JsonFieldType.STRING).description("상호"),
            fieldWithPath("representativeName").type(JsonFieldType.STRING).description("대표자명"),
            fieldWithPath("businessLicenseUrl").type(JsonFieldType.STRING).description("사업자등록증 이미지 URL"),
            fieldWithPath("status").type(JsonFieldType.STRING).description("상태. PENDING | APPROVED | REJECTED"),
            fieldWithPath("rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유(거절 시에만)"),
            fieldWithPath("submittedAt").type(JsonFieldType.STRING).optional().description("신청 시각(ISO-8601)"),
            fieldWithPath("reviewedAt").type(JsonFieldType.STRING).optional().description("심사 시각(ISO-8601)"),
        )

    private val userRowFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("유저 ID"),
            fieldWithPath("email").type(JsonFieldType.STRING).optional().description("이메일"),
            fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
            fieldWithPath("storeName").type(JsonFieldType.STRING).optional().description("가게명"),
            fieldWithPath("isActive").type(JsonFieldType.BOOLEAN).description("활성 여부"),
            fieldWithPath("isAdmin").type(JsonFieldType.BOOLEAN).description("운영자 여부"),
            fieldWithPath("verificationStatus").type(JsonFieldType.STRING).optional().description("사업자 인증 상태(없으면 null)"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).optional().description("가입 시각(ISO-8601)"),
        )

    @Test
    fun `운영자 진입 확인 문서화`() {
        val token = adminToken()
        mockMvc
            .get("/admin/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-me",
                        responseSchema = "AdminMeResponse",
                        tag = "Admin",
                        summary = "운영자 진입 확인(@RequiresAdmin 통과 여부)",
                        responseFields =
                            listOf(
                                fieldWithPath("isAdmin").type(JsonFieldType.BOOLEAN).description("운영자 여부(항상 true)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `AI 헬스 문서화`() {
        val token = adminToken()
        Mockito
            .`when`(aiHealthService.check())
            .thenReturn(
                AiHealthResponse(
                    listOf(
                        AiHealthTarget("ai-server", "UP", 12L, null),
                        AiHealthTarget("litellm", "DOWN", 5L, "연결 거부"),
                    ),
                ),
            )

        mockMvc
            .get("/admin/health/ai") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-health-ai",
                        responseSchema = "AiHealthResponse",
                        tag = "Admin",
                        summary = "AI(ai-server/litellm) 헬스 프록시(상태·지연만)",
                        responseFields =
                            listOf(
                                fieldWithPath("targets").type(JsonFieldType.ARRAY).description("헬스 타깃 목록"),
                                fieldWithPath("targets[].name").type(JsonFieldType.STRING).description("타깃명"),
                                fieldWithPath("targets[].status").type(JsonFieldType.STRING).description("UP | DOWN"),
                                fieldWithPath("targets[].latencyMs").type(JsonFieldType.NUMBER).optional().description("지연(ms)"),
                                fieldWithPath("targets[].detail").type(JsonFieldType.STRING).optional().description("DOWN 사유(안전 분류)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `인증 신청 목록 문서화`() {
        val token = adminToken()
        pendingVerification(registerUser())

        mockMvc
            .get("/admin/verifications?status=PENDING") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-verification-list",
                        responseSchema = "AdminVerificationListResponse",
                        tag = "Admin",
                        summary = "사업자 인증 신청 목록(상태 필터)",
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("신청 목록")) +
                                verificationFields.map {
                                    fieldWithPath("[].${it.path}")
                                        .type(it.type)
                                        .also { f ->
                                            if (it.isOptional) f.optional()
                                        }.description(it.description)
                                },
                    ),
                )
            }
    }

    @Test
    fun `인증 승인 문서화`() {
        val token = adminToken()
        val id = pendingVerification(registerUser())

        mockMvc
            .post("/admin/verifications/$id/approve") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/admin/verifications/{id}/approve")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-verification-approve",
                        responseSchema = "AdminVerificationResponse",
                        tag = "Admin",
                        summary = "사업자 인증 승인",
                        pathParameters = listOf(parameterWithName("id").description("인증 신청 ID")),
                        responseFields = verificationFields,
                    ),
                )
            }
    }

    @Test
    fun `인증 거절 문서화`() {
        val token = adminToken()
        val id = pendingVerification(registerUser())

        mockMvc
            .post("/admin/verifications/$id/reject") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/admin/verifications/{id}/reject")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("reason" to "사업자등록증 사진이 흐립니다"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-verification-reject",
                        requestSchema = "AdminVerificationRejectRequest",
                        responseSchema = "AdminVerificationResponse",
                        tag = "Admin",
                        summary = "사업자 인증 거절(사유 필수)",
                        pathParameters = listOf(parameterWithName("id").description("인증 신청 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("reason").type(JsonFieldType.STRING).description("거절 사유"),
                            ),
                        responseFields = verificationFields,
                    ),
                )
            }
    }

    @Test
    fun `통계 개요 문서화`() {
        val token = adminToken()

        mockMvc
            .get("/admin/stats/overview?range=30d") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-stats-overview",
                        responseSchema = "AdminOverviewResponse",
                        tag = "Admin",
                        summary = "운영 개요 집계(cross-tenant). range=7d|30d|90d|all",
                        responseFields =
                            listOf(
                                fieldWithPath("users.total").type(JsonFieldType.NUMBER).description("전체 유저 수"),
                                fieldWithPath("users.active").type(JsonFieldType.NUMBER).description("활성 유저 수"),
                                fieldWithPath("users.onboarded").type(JsonFieldType.NUMBER).description("온보딩 완료 수"),
                                fieldWithPath("sales.entryCount").type(JsonFieldType.NUMBER).description("매출 건수"),
                                fieldWithPath("sales.totalAmount").type(JsonFieldType.NUMBER).description("매출 합계(원)"),
                                fieldWithPath("sales.last30dCount").type(JsonFieldType.NUMBER).description("최근 30일 매출 건수"),
                                fieldWithPath("verifications.pending").type(JsonFieldType.NUMBER).description("인증 대기 수"),
                                fieldWithPath("verifications.approved").type(JsonFieldType.NUMBER).description("인증 승인 수"),
                                fieldWithPath("verifications.rejected").type(JsonFieldType.NUMBER).description("인증 거절 수"),
                                fieldWithPath("subscriptions.active").type(JsonFieldType.NUMBER).description("활성 구독 수"),
                                fieldWithPath("subscriptions.trialing").type(JsonFieldType.NUMBER).description("트라이얼 구독 수"),
                                fieldWithPath("subscriptions.inGrace").type(JsonFieldType.NUMBER).description("유예 구독 수"),
                                fieldWithPath("subscriptions.expired").type(JsonFieldType.NUMBER).description("만료 구독 수"),
                                fieldWithPath(
                                    "comparison",
                                ).type(JsonFieldType.OBJECT).optional().description("직전 동기간 대비(range=all이면 null)"),
                                fieldWithPath("comparison.usersChangePct").type(JsonFieldType.NUMBER).optional().description("유저 증감(%)"),
                                fieldWithPath(
                                    "comparison.salesCountChangePct",
                                ).type(JsonFieldType.NUMBER).optional().description("매출 건수 증감(%)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `시계열 문서화`() {
        val token = adminToken()

        mockMvc
            .get("/admin/stats/timeseries?metric=signups&range=7d") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-stats-timeseries",
                        responseSchema = "AdminTimeseriesListResponse",
                        tag = "Admin",
                        summary = "일별 시계열. metric=signups|sales, range=7d|30d|90d",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("일별 점 목록"),
                                fieldWithPath("[].date").type(JsonFieldType.STRING).description("날짜(yyyy-MM-dd)"),
                                fieldWithPath("[].count").type(JsonFieldType.NUMBER).description("해당일 값"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `유저 목록 문서화`() {
        val token = adminToken()
        registerUser()

        mockMvc
            .get("/admin/users?page=0&size=50") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-user-list",
                        responseSchema = "AdminUserPage",
                        tag = "Admin",
                        summary = "유저 목록(이메일/닉네임 검색·페이지)",
                        responseFields =
                            listOf(
                                fieldWithPath("rows").type(JsonFieldType.ARRAY).description("유저 행 목록"),
                                fieldWithPath("page").type(JsonFieldType.NUMBER).description("현재 페이지(0-base)"),
                                fieldWithPath("size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                                fieldWithPath("total").type(JsonFieldType.NUMBER).description("전체 건수"),
                            ) +
                                userRowFields.map {
                                    fieldWithPath("rows[].${it.path}")
                                        .type(it.type)
                                        .also { f ->
                                            if (it.isOptional) f.optional()
                                        }.description(it.description)
                                },
                    ),
                )
            }
    }

    @Test
    fun `유저 상세 문서화`() {
        val token = adminToken()
        val targetId = registerUser()

        mockMvc
            .get("/admin/users/$targetId") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/admin/users/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-user-detail",
                        responseSchema = "AdminUserDetail",
                        tag = "Admin",
                        summary = "유저 상세(프로필·구독·인증이력·매출요약)",
                        pathParameters = listOf(parameterWithName("id").description("유저 ID")),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("유저 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).optional().description("이메일"),
                                fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                                fieldWithPath("isActive").type(JsonFieldType.BOOLEAN).description("활성 여부"),
                                fieldWithPath("isAdmin").type(JsonFieldType.BOOLEAN).description("운영자 여부"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).optional().description("가입 시각(ISO-8601)"),
                                fieldWithPath("storeName").type(JsonFieldType.STRING).optional().description("가게명"),
                                fieldWithPath("regionSido").type(JsonFieldType.STRING).optional().description("시/도"),
                                fieldWithPath("regionSigungu").type(JsonFieldType.STRING).optional().description("시군구"),
                                fieldWithPath("verifications").type(JsonFieldType.ARRAY).description("사업자 인증 이력"),
                                fieldWithPath("verifications[].status").type(JsonFieldType.STRING).optional().description("상태"),
                                fieldWithPath("verifications[].submittedAt").type(JsonFieldType.STRING).optional().description("신청 시각"),
                                fieldWithPath("verifications[].reviewedAt").type(JsonFieldType.STRING).optional().description("심사 시각"),
                                fieldWithPath("verifications[].rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
                                fieldWithPath("salesCount").type(JsonFieldType.NUMBER).description("매출 건수"),
                                fieldWithPath("salesTotal").type(JsonFieldType.NUMBER).description("매출 합계(원)"),
                                fieldWithPath("lastSaleDate").type(JsonFieldType.STRING).optional().description("최근 매출일(yyyy-MM-dd)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `유저 활성화 토글 문서화`() {
        val token = adminToken()
        val targetId = registerUser()

        mockMvc
            .post("/admin/users/$targetId/active") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/admin/users/{id}/active")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("active" to false))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "admin-user-set-active",
                        requestSchema = "SetActiveRequest",
                        responseSchema = "AdminUserRow",
                        tag = "Admin",
                        summary = "유저 활성/비활성 전환(본인 비활성화 불가)",
                        pathParameters = listOf(parameterWithName("id").description("유저 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("active").type(JsonFieldType.BOOLEAN).description("활성 여부"),
                            ),
                        responseFields = userRowFields,
                    ),
                )
            }
    }
}
