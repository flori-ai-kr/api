package kr.ai.flori.insights.docs

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.insights.repository.InstagramAccountRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

/**
 * InternalInsightController RestDocs 문서화.
 * Bearer 내부 키(internal.api-key) 인증 — JWT 아님.
 * 기본 컨텍스트에서는 internal.api-key=""(빈 값)이어서 403이 반환되므로
 * 별도 SpringBootTest(properties)로 테스트 키를 주입한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["internal.api-key=test-internal-key-docs"])
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class InternalInsightDocsTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var accountRepository: InstagramAccountRepository

    /** 테스트용 내부 API 키 (SpringBootTest properties에서 주입한 값과 동일) */
    private val internalKey = "test-internal-key-docs"

    private fun json(value: Any): String = objectMapper.writeValueAsString(value)

    /** docs() 헬퍼 — RestDocsSupport와 동일한 패턴 (pathParameters 지원 포함) */
    private fun docs(
        identifier: String,
        tag: String,
        summary: String,
        pathParameters: List<ParameterDescriptor> = emptyList(),
        requestFields: List<FieldDescriptor> = emptyList(),
        responseFields: List<FieldDescriptor> = emptyList(),
    ): ResultHandler {
        val params = ResourceSnippetParameters.builder().tag(tag).summary(summary)
        if (pathParameters.isNotEmpty()) params.pathParameters(*pathParameters.toTypedArray())
        if (requestFields.isNotEmpty()) params.requestFields(*requestFields.toTypedArray())
        if (responseFields.isNotEmpty()) params.responseFields(*responseFields.toTypedArray())
        return MockMvcRestDocumentationWrapper.document(identifier, snippets = arrayOf(resource(params.build())))
    }

    /** InstagramAccountResponse 공통 응답 필드 */
    private val accountResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("계정 ID"),
            fieldWithPath("username").type(JsonFieldType.STRING).description("인스타그램 유저네임"),
            fieldWithPath("displayName").type(JsonFieldType.STRING).optional().description("표시명 (null 가능)"),
            fieldWithPath("profileUrl").type(JsonFieldType.STRING).description("프로필 URL"),
            fieldWithPath("region").type(JsonFieldType.STRING).description("지역"),
            fieldWithPath("sortOrder").type(JsonFieldType.NUMBER).description("정렬 순서"),
            fieldWithPath("active").type(JsonFieldType.BOOLEAN).description("활성 여부"),
            fieldWithPath("notes").type(JsonFieldType.STRING).optional().description("메모 (null 가능)"),
        )

    /** IngestResultResponse 공통 응답 필드 */
    private val ingestResultFields =
        listOf(
            fieldWithPath("inserted").type(JsonFieldType.NUMBER).description("신규 적재 건수"),
            fieldWithPath("skipped").type(JsonFieldType.NUMBER).description("중복으로 스킵된 건수"),
        )

    /** 계정 생성 후 id 반환 (다른 테스트에서 업데이트·삭제 대상으로 활용) */
    private fun createAccount(username: String): Long {
        val res =
            mockMvc
                .post("/internal/instagram-accounts") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $internalKey")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "username" to username,
                                "displayName" to "테스트 계정",
                                "region" to "domestic",
                                "sortOrder" to 0,
                                "active" to true,
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asLong()
    }

    // ── 1. 트렌드 수집 ─────────────────────────────────────────────────────────

    @Test
    fun `트렌드 수집 문서화`() {
        mockMvc
            .post("/internal/trends") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $internalKey")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "articles" to
                                listOf(
                                    mapOf(
                                        "category" to "flower",
                                        "title" to "봄 꽃꽂이 글로벌 트렌드",
                                        "summary" to "2026년 봄 시즌 글로벌 플로럴 디자인 트렌드 요약",
                                        "keyPoints" to listOf("자연주의 스타일 부상", "지속가능성 강조"),
                                        "sourceUrl" to "https://example.com/trend/${UUID.randomUUID()}",
                                        "sourceName" to "FlowerTrend Magazine",
                                        "publishedAt" to Instant.now().toString(),
                                    ),
                                ),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "internal-ingest-trends",
                        tag = "Internal",
                        summary = "[내부 API] 트렌드 기사 수집 (멱등 — source_url 중복 스킵, 신규 시 FCM 브로드캐스트)",
                        requestFields =
                            listOf(
                                fieldWithPath("articles")
                                    .type(JsonFieldType.ARRAY)
                                    .description("수집할 트렌드 기사 목록 (1개 이상, 필수)"),
                                fieldWithPath("articles[].category")
                                    .type(JsonFieldType.STRING)
                                    .description("카테고리 (flower | inspiration | business | industry, 필수)"),
                                fieldWithPath("articles[].title")
                                    .type(JsonFieldType.STRING)
                                    .description("제목 (필수)"),
                                fieldWithPath("articles[].summary")
                                    .type(JsonFieldType.STRING)
                                    .description("요약 (필수)"),
                                fieldWithPath("articles[].keyPoints")
                                    .type(JsonFieldType.ARRAY)
                                    .optional()
                                    .description("핵심 포인트 목록 (기본값: 빈 배열)"),
                                fieldWithPath("articles[].sourceUrl")
                                    .type(JsonFieldType.STRING)
                                    .description("원본 URL — 멱등 키 (필수)"),
                                fieldWithPath("articles[].sourceName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("출처명 (null 가능)"),
                                fieldWithPath("articles[].publishedAt")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("발행 시각 (ISO-8601, null 가능)"),
                            ),
                        responseFields = ingestResultFields,
                    ),
                )
            }
    }

    // ── 2. 인스타 포스트 수집 ──────────────────────────────────────────────────

    @Test
    fun `인스타 포스트 수집 문서화`() {
        // 공유 시드 계정 사용 (docs/sql/seed.sql)
        val account = accountRepository.findByActiveTrueOrderBySortOrderAscUsernameAsc().first()
        val accountId = requireNotNull(account.id)

        mockMvc
            .post("/internal/instagram-posts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $internalKey")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "posts" to
                                listOf(
                                    mapOf(
                                        "accountId" to accountId,
                                        "shortcode" to "DOCS${UUID.randomUUID().toString().take(8)}",
                                        "permalink" to "https://www.instagram.com/p/docs_post/",
                                        "imageUrls" to listOf("https://cdn.example.com/img1.jpg"),
                                        "caption" to "봄 꽃꽂이 인스피레이션",
                                        "likeCount" to 128,
                                        "postedAt" to Instant.now().toString(),
                                    ),
                                ),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "internal-ingest-instagram-posts",
                        tag = "Internal",
                        summary = "[내부 API] 인스타 포스트 수집 (멱등 — shortcode 중복 스킵)",
                        requestFields =
                            listOf(
                                fieldWithPath("posts")
                                    .type(JsonFieldType.ARRAY)
                                    .description("수집할 포스트 목록 (1개 이상, 필수)"),
                                fieldWithPath("posts[].accountId")
                                    .type(JsonFieldType.NUMBER)
                                    .description("계정 ID (필수)"),
                                fieldWithPath("posts[].shortcode")
                                    .type(JsonFieldType.STRING)
                                    .description("인스타그램 shortcode — 멱등 키 (필수)"),
                                fieldWithPath("posts[].permalink")
                                    .type(JsonFieldType.STRING)
                                    .description("퍼머링크 URL (필수)"),
                                fieldWithPath("posts[].imageUrls")
                                    .type(JsonFieldType.ARRAY)
                                    .optional()
                                    .description("이미지 URL 목록 (기본값: 빈 배열)"),
                                fieldWithPath("posts[].caption")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("캡션 (null 가능)"),
                                fieldWithPath("posts[].likeCount")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("좋아요 수 (기본값: 0)"),
                                fieldWithPath("posts[].postedAt")
                                    .type(JsonFieldType.STRING)
                                    .description("게시 시각 (ISO-8601, 필수)"),
                            ),
                        responseFields = ingestResultFields,
                    ),
                )
            }
    }

    // ── 3. 인스타 계정 등록 ────────────────────────────────────────────────────

    @Test
    fun `인스타 계정 등록 문서화`() {
        val username = "docs_create_${UUID.randomUUID().toString().take(8)}"

        mockMvc
            .post("/internal/instagram-accounts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $internalKey")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "username" to username,
                            "displayName" to "문서화 테스트 계정",
                            "region" to "domestic",
                            "sortOrder" to 5,
                            "active" to true,
                            "notes" to "문서화용 계정",
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "internal-create-instagram-account",
                        tag = "Internal",
                        summary = "[내부 API] 인스타 계정 등록 (201 Created)",
                        requestFields =
                            listOf(
                                fieldWithPath("username")
                                    .type(JsonFieldType.STRING)
                                    .description("인스타그램 유저네임 (필수, 계정 내 유일)"),
                                fieldWithPath("displayName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("표시명 (null 가능)"),
                                fieldWithPath("region")
                                    .type(JsonFieldType.STRING)
                                    .description("지역 (domestic | international, 필수)"),
                                fieldWithPath("sortOrder")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("정렬 순서 (기본값: 0)"),
                                fieldWithPath("active")
                                    .type(JsonFieldType.BOOLEAN)
                                    .optional()
                                    .description("활성 여부 (기본값: true)"),
                                fieldWithPath("notes")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 (null 가능)"),
                            ),
                        responseFields = accountResponseFields,
                    ),
                )
            }
    }

    // ── 4. 인스타 계정 수정 ────────────────────────────────────────────────────

    @Test
    fun `인스타 계정 수정 문서화`() {
        val id = createAccount("docs_update_${UUID.randomUUID().toString().take(8)}")

        mockMvc
            .put("/internal/instagram-accounts/$id") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/internal/instagram-accounts/{id}",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $internalKey")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "displayName" to "수정된 표시명",
                            "sortOrder" to 10,
                            "active" to false,
                            "notes" to "수정된 메모",
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "internal-update-instagram-account",
                        tag = "Internal",
                        summary = "[내부 API] 인스타 계정 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("수정할 계정 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("username")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("유저네임 변경 (변경 시 profileUrl도 자동 갱신)"),
                                fieldWithPath("displayName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("표시명 변경 (null 가능)"),
                                fieldWithPath("region")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("지역 변경"),
                                fieldWithPath("sortOrder")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("정렬 순서 변경"),
                                fieldWithPath("active")
                                    .type(JsonFieldType.BOOLEAN)
                                    .optional()
                                    .description("활성 여부 변경"),
                                fieldWithPath("notes")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 변경 (null 가능)"),
                            ),
                        responseFields = accountResponseFields,
                    ),
                )
            }
    }

    // ── 5. 인스타 계정 삭제 ────────────────────────────────────────────────────

    @Test
    fun `인스타 계정 삭제 문서화`() {
        val id = createAccount("docs_delete_${UUID.randomUUID().toString().take(8)}")

        mockMvc
            .delete("/internal/instagram-accounts/$id") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/internal/instagram-accounts/{id}",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $internalKey")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "internal-delete-instagram-account",
                        tag = "Internal",
                        summary = "[내부 API] 인스타 계정 삭제 (204 No Content)",
                        pathParameters = listOf(parameterWithName("id").description("삭제할 계정 ID")),
                    ),
                )
            }
    }
}
