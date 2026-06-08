package kr.ai.flori.customers.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/**
 * Customer Grades(고객 등급) API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * 첫 목록 조회 시 기본 등급 4종(신규/단골/VIP/블랙리스트)이 시드로 삽입된다.
 */
class CustomerGradeDocsTest : RestDocsSupport() {
    /** CustomerGradeResponse 공통 응답 필드 — 단건(생성/수정)에서 재사용 */
    private val gradeResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("등급 ID"),
            fieldWithPath("name").type(JsonFieldType.STRING).description("등급명"),
            fieldWithPath("threshold")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("자동 승급 임계 구매횟수 (null이면 수동 전용 등급)"),
            fieldWithPath("sortOrder").type(JsonFieldType.NUMBER).description("정렬 순서"),
        )

    /** 테스트용 등급 생성 → 생성된 id 반환 (이름 충돌 회피를 위해 suffix 부여) */
    private fun createGrade(token: String): String {
        val suffix = System.nanoTime().toString().takeLast(6)
        val res =
            mockMvc
                .post("/customer-grades") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("name" to "테스트등급_$suffix", "threshold" to 20))
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 고객 등급 목록 조회 ─────────────────────────────────────────────────

    @Test
    fun `고객 등급 목록 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/customer-grades") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-grade-list",
                        responseSchema = "CustomerGradeListResponse",
                        tag = "CustomerGrades",
                        summary = "고객 등급 목록 (정렬 순서 오름차순, 첫 조회 시 기본 4종 시드)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("등급 목록"),
                                fieldWithPath("[].id")
                                    .type(JsonFieldType.NUMBER)
                                    .description("등급 ID"),
                                fieldWithPath("[].name")
                                    .type(JsonFieldType.STRING)
                                    .description("등급명"),
                                fieldWithPath("[].threshold")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("자동 승급 임계 구매횟수 (null이면 수동 전용 등급)"),
                                fieldWithPath("[].sortOrder")
                                    .type(JsonFieldType.NUMBER)
                                    .description("정렬 순서"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 고객 등급 생성 ──────────────────────────────────────────────────────

    @Test
    fun `고객 등급 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/customer-grades") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "플래티넘", "threshold" to 30))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-grade-create",
                        requestSchema = "CustomerGradeCreateRequest",
                        responseSchema = "CustomerGradeResponse",
                        tag = "CustomerGrades",
                        summary = "고객 등급 생성 (등급명 중복 불가, sortOrder 자동 채번)",
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .description("등급명 (필수, 계정 내 유일)"),
                                fieldWithPath("threshold")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("자동 승급 임계 구매횟수 (0 이상, 생략 시 수동 전용 등급)"),
                            ),
                        responseFields = gradeResponseFields,
                    ),
                )
            }
    }

    // ── 3. 고객 등급 수정 ──────────────────────────────────────────────────────

    @Test
    fun `고객 등급 수정 문서화`() {
        val token = signupAndToken()
        val id = createGrade(token)

        mockMvc
            .patch("/customer-grades/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customer-grades/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "수정된 등급",
                            "threshold" to 50,
                            "sortOrder" to 9,
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-grade-update",
                        requestSchema = "CustomerGradeUpdateRequest",
                        responseSchema = "CustomerGradeResponse",
                        tag = "CustomerGrades",
                        summary = "고객 등급 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("등급 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("등급명 변경 (계정 내 유일)"),
                                fieldWithPath("threshold")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("자동 승급 임계 구매횟수 변경. NULL 처리는 clearThreshold 사용"),
                                fieldWithPath("sortOrder")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("정렬 순서 변경"),
                                fieldWithPath("clearThreshold")
                                    .type(JsonFieldType.BOOLEAN)
                                    .optional()
                                    .description("true면 threshold를 명시적으로 NULL(수동 전용)로 변경 (기본 false)"),
                            ),
                        responseFields = gradeResponseFields,
                    ),
                )
            }
    }

    // ── 4. 고객 등급 삭제 ──────────────────────────────────────────────────────

    @Test
    fun `고객 등급 삭제 문서화`() {
        val token = signupAndToken()
        // 기본 4종 시드 + 1개 추가 생성 → 삭제해도 "최소 1개" 제약에 걸리지 않음
        mockMvc.get("/customer-grades") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andReturn()
        val id = createGrade(token)

        mockMvc
            .delete("/customer-grades/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customer-grades/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "customer-grade-delete",
                        tag = "CustomerGrades",
                        summary = "고객 등급 삭제 (최소 1개 유지, 참조 고객의 등급은 NULL 처리)",
                        pathParameters = listOf(parameterWithName("id").description("등급 ID")),
                    ),
                )
            }
    }
}
