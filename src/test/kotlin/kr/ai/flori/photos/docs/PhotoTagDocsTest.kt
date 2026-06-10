package kr.ai.flori.photos.docs

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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * PhotoTags API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 * 태그는 이름만 가진다(색상 개념 폐기 — #해시태그 텍스트 표시).
 */
class PhotoTagDocsTest : RestDocsSupport() {
    /** PhotoTagResponse 공통 응답 필드 — 단건/목록에서 재사용 */
    private val photoTagResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("태그 ID"),
            fieldWithPath("name").type(JsonFieldType.STRING).description("태그 이름"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
        )

    /** 테스트용 태그 생성 → 생성된 id 반환 */
    private fun createTag(
        token: String,
        name: String = "웨딩",
    ): String {
        val res =
            mockMvc
                .post("/photo-tags") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("name" to name))
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 태그 목록 조회 ──────────────────────────────────────────────────────

    @Test
    fun `태그 목록 조회 문서화`() {
        val token = signupAndToken()
        createTag(token, "웨딩")
        createTag(token, "부케")

        mockMvc
            .get("/photo-tags") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-tag-list",
                        responseSchema = "PhotoTagListResponse",
                        tag = "PhotoTags",
                        summary = "태그 목록 (본인 계정 태그 전체)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("태그 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("태그 ID"),
                                fieldWithPath("[].name").type(JsonFieldType.STRING).description("태그 이름"),
                                fieldWithPath("[].createdAt")
                                    .type(JsonFieldType.STRING)
                                    .description("생성 시각 (ISO-8601)"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 태그 생성 ───────────────────────────────────────────────────────────

    @Test
    fun `태그 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/photo-tags") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "행사",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-tag-create",
                        requestSchema = "PhotoTagCreateRequest",
                        responseSchema = "PhotoTagResponse",
                        tag = "PhotoTags",
                        summary = "태그 생성 (이름만, 계정 내 유일)",
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .description("태그 이름 (필수, 계정 내 유일)"),
                            ),
                        responseFields = photoTagResponseFields,
                    ),
                )
            }
    }

    // ── 3. 태그 수정 ───────────────────────────────────────────────────────────

    @Test
    fun `태그 수정 문서화`() {
        val token = signupAndToken()
        val id = createTag(token, "수정전태그")

        mockMvc
            .put("/photo-tags/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-tags/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "name" to "수정후태그",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-tag-update",
                        requestSchema = "PhotoTagUpdateRequest",
                        responseSchema = "PhotoTagResponse",
                        tag = "PhotoTags",
                        summary = "태그 수정 (이름 교체)",
                        pathParameters = listOf(parameterWithName("id").description("태그 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("name")
                                    .type(JsonFieldType.STRING)
                                    .description("새 태그 이름 (필수)"),
                            ),
                        responseFields = photoTagResponseFields,
                    ),
                )
            }
    }

    // ── 4. 태그 삭제 ───────────────────────────────────────────────────────────

    @Test
    fun `태그 삭제 문서화`() {
        val token = signupAndToken()
        val id = createTag(token, "삭제대상태그")

        mockMvc
            .delete("/photo-tags/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-tags/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-tag-delete",
                        tag = "PhotoTags",
                        summary = "태그 삭제 (사용 중인 모든 사진 카드에서도 자동 제거)",
                        pathParameters = listOf(parameterWithName("id").description("태그 ID")),
                    ),
                )
            }
    }
}
