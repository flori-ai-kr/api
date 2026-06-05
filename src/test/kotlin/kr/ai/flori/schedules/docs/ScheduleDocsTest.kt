package kr.ai.flori.schedules.docs

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
 * Schedules API RestDocs 문서화.
 */
class ScheduleDocsTest : RestDocsSupport() {
    private val scheduleResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("일정 ID"),
            fieldWithPath("title").type(JsonFieldType.STRING).description("일정 제목"),
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("시작일 (yyyy-MM-dd)"),
            fieldWithPath("endDate").type(JsonFieldType.STRING).description("종료일 (yyyy-MM-dd)"),
            fieldWithPath("color")
                .type(JsonFieldType.STRING)
                .description("표시 색상 (기본값 #f43f5e)"),
            fieldWithPath("memo")
                .type(JsonFieldType.STRING)
                .optional()
                .description("일정 메모 (미입력이면 null)"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    private fun createSchedule(token: String): String {
        val res =
            mockMvc
                .post("/schedules") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "title" to "워크숍",
                                "startDate" to "2026-06-01",
                                "endDate" to "2026-06-03",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 일정 생성 ─────────────────────────────────────────────────────────

    @Test
    fun `일정 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/schedules") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "title" to "플로리스트 교육",
                            "startDate" to "2026-06-10",
                            "endDate" to "2026-06-12",
                            "color" to "#1565C0",
                            "memo" to "꽃꽂이 심화 과정",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "schedule-create",
                        requestSchema = "ScheduleCreateRequest",
                        responseSchema = "ScheduleResponse",
                        tag = "Schedules",
                        summary = "일정 생성",
                        requestFields =
                            listOf(
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .description("일정 제목 (필수)"),
                                fieldWithPath("startDate")
                                    .type(JsonFieldType.STRING)
                                    .description("시작일 (yyyy-MM-dd, 필수)"),
                                fieldWithPath("endDate")
                                    .type(JsonFieldType.STRING)
                                    .description("종료일 (yyyy-MM-dd, 필수, startDate 이상)"),
                                fieldWithPath("color")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("표시 색상 (hex, 기본값 #f43f5e)"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("일정 메모"),
                            ),
                        responseFields = scheduleResponseFields,
                    ),
                )
            }
    }

    // ── 2. 월별 일정 목록 ────────────────────────────────────────────────────

    @Test
    fun `월별 일정 목록 문서화`() {
        val token = signupAndToken()
        createSchedule(token)

        mockMvc
            .get("/schedules") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("month", "2026-06")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "schedule-list",
                        responseSchema = "ScheduleListResponse",
                        tag = "Schedules",
                        summary = "월별 일정 목록 (월 범위와 겹치는 일정 포함)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("일정 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("일정 ID"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).description("일정 제목"),
                                fieldWithPath("[].startDate")
                                    .type(JsonFieldType.STRING)
                                    .description("시작일 (yyyy-MM-dd)"),
                                fieldWithPath("[].endDate")
                                    .type(JsonFieldType.STRING)
                                    .description("종료일 (yyyy-MM-dd)"),
                                fieldWithPath("[].color")
                                    .type(JsonFieldType.STRING)
                                    .description("표시 색상 (hex)"),
                                fieldWithPath("[].memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("일정 메모 (미입력이면 null)"),
                                fieldWithPath("[].createdAt")
                                    .type(JsonFieldType.STRING)
                                    .description("생성 시각 (ISO-8601)"),
                                fieldWithPath("[].updatedAt")
                                    .type(JsonFieldType.STRING)
                                    .description("최종 수정 시각 (ISO-8601)"),
                            ),
                    ),
                )
            }
    }

    // ── 3. 일정 단건 조회 ────────────────────────────────────────────────────

    @Test
    fun `일정 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createSchedule(token)

        mockMvc
            .get("/schedules/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/schedules/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "schedule-get",
                        responseSchema = "ScheduleResponse",
                        tag = "Schedules",
                        summary = "일정 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("일정 ID")),
                        responseFields = scheduleResponseFields,
                    ),
                )
            }
    }

    // ── 4. 일정 수정 ─────────────────────────────────────────────────────────

    @Test
    fun `일정 수정 문서화`() {
        val token = signupAndToken()
        val id = createSchedule(token)

        mockMvc
            .patch("/schedules/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/schedules/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "title" to "수정된 일정",
                            "color" to "#C62828",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "schedule-update",
                        requestSchema = "ScheduleUpdateRequest",
                        responseSchema = "ScheduleResponse",
                        tag = "Schedules",
                        summary = "일정 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("일정 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("제목 변경"),
                                fieldWithPath("startDate")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("시작일 변경 (yyyy-MM-dd)"),
                                fieldWithPath("endDate")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("종료일 변경 (yyyy-MM-dd, startDate 이상)"),
                                fieldWithPath("color")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("색상 변경 (hex)"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 변경"),
                            ),
                        responseFields = scheduleResponseFields,
                    ),
                )
            }
    }

    // ── 5. 일정 삭제 ─────────────────────────────────────────────────────────

    @Test
    fun `일정 삭제 문서화`() {
        val token = signupAndToken()
        val id = createSchedule(token)

        mockMvc
            .delete("/schedules/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/schedules/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "schedule-delete",
                        tag = "Schedules",
                        summary = "일정 삭제",
                        pathParameters = listOf(parameterWithName("id").description("일정 ID")),
                    ),
                )
            }
    }
}
