package kr.ai.flori.calendar.docs

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
 * CalendarEvents API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * endDate는 startDate 이상이어야 한다(서버 검증).
 */
class CalendarEventDocsTest : RestDocsSupport() {
    /** CalendarEventResponse 공통 응답 필드 — 단건 조회/생성/수정에서 재사용 */
    private val calendarEventResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.STRING).description("이벤트 UUID"),
            fieldWithPath("title").type(JsonFieldType.STRING).description("이벤트 제목"),
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("시작일 (yyyy-MM-dd)"),
            fieldWithPath("endDate").type(JsonFieldType.STRING).description("종료일 (yyyy-MM-dd)"),
            fieldWithPath("color")
                .type(JsonFieldType.STRING)
                .description("표시 색상 (기본값 #4CAF50)"),
            fieldWithPath("description")
                .type(JsonFieldType.STRING)
                .optional()
                .description("이벤트 설명 (미입력이면 null)"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** 테스트용 이벤트 생성 → 생성된 id 반환 */
    private fun createEvent(token: String): String {
        val res =
            mockMvc
                .post("/calendar-events") {
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

    // ── 1. 이벤트 생성 ─────────────────────────────────────────────────────────

    @Test
    fun `이벤트 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/calendar-events") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "title" to "플로리스트 교육",
                            "startDate" to "2026-06-10",
                            "endDate" to "2026-06-12",
                            "color" to "#1565C0",
                            "description" to "꽃꽂이 심화 과정",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "calendar-event-create",
                        tag = "CalendarEvents",
                        summary = "캘린더 이벤트 생성",
                        requestFields =
                            listOf(
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .description("이벤트 제목 (필수)"),
                                fieldWithPath("startDate")
                                    .type(JsonFieldType.STRING)
                                    .description("시작일 (yyyy-MM-dd, 필수)"),
                                fieldWithPath("endDate")
                                    .type(JsonFieldType.STRING)
                                    .description("종료일 (yyyy-MM-dd, 필수, startDate 이상)"),
                                fieldWithPath("color")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("표시 색상 (hex, 기본값 #4CAF50)"),
                                fieldWithPath("description")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("이벤트 설명"),
                            ),
                        responseFields = calendarEventResponseFields,
                    ),
                )
            }
    }

    // ── 2. 월별 이벤트 목록 ────────────────────────────────────────────────────

    @Test
    fun `월별 이벤트 목록 문서화`() {
        val token = signupAndToken()
        createEvent(token)

        mockMvc
            .get("/calendar-events") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("month", "2026-06")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "calendar-event-list",
                        tag = "CalendarEvents",
                        summary = "월별 이벤트 목록 (월 범위와 겹치는 이벤트 포함)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("이벤트 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.STRING).description("이벤트 UUID"),
                                fieldWithPath("[].title").type(JsonFieldType.STRING).description("이벤트 제목"),
                                fieldWithPath("[].startDate")
                                    .type(JsonFieldType.STRING)
                                    .description("시작일 (yyyy-MM-dd)"),
                                fieldWithPath("[].endDate")
                                    .type(JsonFieldType.STRING)
                                    .description("종료일 (yyyy-MM-dd)"),
                                fieldWithPath("[].color")
                                    .type(JsonFieldType.STRING)
                                    .description("표시 색상 (hex)"),
                                fieldWithPath("[].description")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("이벤트 설명 (미입력이면 null)"),
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

    // ── 3. 이벤트 단건 조회 ────────────────────────────────────────────────────

    @Test
    fun `이벤트 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createEvent(token)

        mockMvc
            .get("/calendar-events/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/calendar-events/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "calendar-event-get",
                        tag = "CalendarEvents",
                        summary = "캘린더 이벤트 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("이벤트 UUID")),
                        responseFields = calendarEventResponseFields,
                    ),
                )
            }
    }

    // ── 4. 이벤트 수정 ─────────────────────────────────────────────────────────

    @Test
    fun `이벤트 수정 문서화`() {
        val token = signupAndToken()
        val id = createEvent(token)

        mockMvc
            .patch("/calendar-events/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/calendar-events/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "title" to "수정된 이벤트",
                            "color" to "#C62828",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "calendar-event-update",
                        tag = "CalendarEvents",
                        summary = "캘린더 이벤트 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("이벤트 UUID")),
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
                                fieldWithPath("description")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("설명 변경"),
                            ),
                        responseFields = calendarEventResponseFields,
                    ),
                )
            }
    }

    // ── 5. 이벤트 삭제 ─────────────────────────────────────────────────────────

    @Test
    fun `이벤트 삭제 문서화`() {
        val token = signupAndToken()
        val id = createEvent(token)

        mockMvc
            .delete("/calendar-events/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/calendar-events/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "calendar-event-delete",
                        tag = "CalendarEvents",
                        summary = "캘린더 이벤트 삭제",
                        pathParameters = listOf(parameterWithName("id").description("이벤트 UUID")),
                    ),
                )
            }
    }
}
