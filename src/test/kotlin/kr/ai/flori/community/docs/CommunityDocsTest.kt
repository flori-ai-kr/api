package kr.ai.flori.community.docs

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * 커뮤니티 API RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 호출하며 OpenAPI를 생성한다.
 * upload-targets(presigned)는 더미 S3Presigner 빈으로 로컬 서명만 수행한다(네트워크 불요).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(
    properties = [
        "aws.s3.bucket=flori-test-bucket",
        "aws.cloudfront.domain=cdn.flori.dev",
        "aws.region=ap-northeast-2",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class CommunityDocsTest : RestDocsSupport() {
    @TestConfiguration
    @EnableConfigurationProperties
    class TestS3Config {
        @Bean
        @Primary
        fun s3Presigner(): S3Presigner =
            S3Presigner
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy-access-key-id", "dummy-secret-access-key"),
                    ),
                ).build()
    }

    private fun postResponseFields(prefix: String): List<FieldDescriptor> =
        listOf(
            fieldWithPath("${prefix}id").type(JsonFieldType.NUMBER).description("게시글 ID"),
            fieldWithPath("${prefix}authorNickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
            fieldWithPath("${prefix}authorIsAdmin").type(JsonFieldType.BOOLEAN).description("작성자 운영자 여부"),
            fieldWithPath("${prefix}category").type(JsonFieldType.STRING).description("카테고리"),
            fieldWithPath("${prefix}title").type(JsonFieldType.STRING).description("제목"),
            subsectionWithPath("${prefix}content").type(JsonFieldType.OBJECT).description("본문(Tiptap JSON)"),
            fieldWithPath("${prefix}contentText").type(JsonFieldType.STRING).description("본문 plain text"),
            fieldWithPath("${prefix}imageUrls").type(JsonFieldType.ARRAY).description("이미지 URL 목록"),
            fieldWithPath("${prefix}isPinned").type(JsonFieldType.BOOLEAN).description("고정글 여부"),
            fieldWithPath("${prefix}likeCount").type(JsonFieldType.NUMBER).description("좋아요 수"),
            fieldWithPath("${prefix}commentCount").type(JsonFieldType.NUMBER).description("댓글 수"),
            fieldWithPath("${prefix}liked").type(JsonFieldType.BOOLEAN).description("현재 사용자 좋아요 여부"),
            fieldWithPath("${prefix}isMine").type(JsonFieldType.BOOLEAN).description("현재 사용자 작성 여부"),
            fieldWithPath("${prefix}viewerIsAdmin").type(JsonFieldType.BOOLEAN).description("현재 사용자 운영자 여부(고정 등 관리자 액션 노출 판단)"),
            fieldWithPath("${prefix}createdAt").type(JsonFieldType.STRING).description("생성 시각(ISO-8601)"),
            fieldWithPath("${prefix}updatedAt").type(JsonFieldType.STRING).description("수정 시각(ISO-8601)"),
        )

    private val commentItemFields =
        listOf(
            fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("댓글 ID"),
            fieldWithPath("[].postId").type(JsonFieldType.NUMBER).description("게시글 ID"),
            fieldWithPath("[].parentId").type(JsonFieldType.NUMBER).optional().description("부모 댓글 ID(대댓글이면, 아니면 null)"),
            fieldWithPath("[].authorNickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
            fieldWithPath("[].authorIsAdmin").type(JsonFieldType.BOOLEAN).description("작성자 운영자 여부"),
            fieldWithPath("[].content").type(JsonFieldType.STRING).description("내용(비권한 비밀댓글/삭제 댓글은 빈 문자열)"),
            fieldWithPath("[].isSecret").type(JsonFieldType.BOOLEAN).description("비밀댓글 여부"),
            fieldWithPath("[].isMine").type(JsonFieldType.BOOLEAN).description("현재 사용자 작성 여부"),
            fieldWithPath("[].canView").type(JsonFieldType.BOOLEAN).description("열람 권한"),
            fieldWithPath("[].isDeleted").type(JsonFieldType.BOOLEAN).description("삭제 여부(톰스톤)"),
            fieldWithPath("[].createdAt").type(JsonFieldType.STRING).description("생성 시각(ISO-8601)"),
        )

    private fun createPost(token: String): String {
        val res =
            mockMvc
                .post("/community/posts") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "category" to "daily",
                                "title" to "오늘의 꽃 작업",
                                "contentJson" to mapOf("type" to "doc", "content" to emptyList<Any>()),
                                "contentText" to "오늘 작업한 내용입니다",
                                "imageUrls" to listOf("https://cdn.flori.dev/community/1/a.jpg"),
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    private fun createComment(
        token: String,
        postId: String,
    ): String {
        val res =
            mockMvc
                .post("/community/posts/$postId/comments") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("content" to "첫 댓글입니다", "isSecret" to false))
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 게시글 생성 ─────────────────────────────────────────────────────────

    @Test
    fun `게시글 생성 문서화`() {
        val token = signupVerifiedAndToken()

        mockMvc
            .post("/community/posts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "category" to "question",
                            "title" to "수국 관리 질문",
                            "contentJson" to mapOf("type" to "doc", "content" to emptyList<Any>()),
                            "contentText" to "수국이 시들어요",
                            "imageUrls" to emptyList<String>(),
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-post-create",
                        requestSchema = "PostCreateRequest",
                        responseSchema = "PostResponse",
                        tag = "Community",
                        summary = "게시글 생성 (notice는 관리자만)",
                        requestFields =
                            listOf(
                                fieldWithPath("category")
                                    .type(JsonFieldType.STRING)
                                    .description("notice|daily|question|knowledge|review|etc (필수)"),
                                fieldWithPath("title").type(JsonFieldType.STRING).description("제목 (필수)"),
                                subsectionWithPath("contentJson").type(JsonFieldType.OBJECT).description("본문 Tiptap JSON (필수)"),
                                fieldWithPath("contentText").type(JsonFieldType.STRING).optional().description("본문 plain text(검색/미리보기)"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY).optional().description("이미지 URL 목록"),
                            ),
                        responseFields = postResponseFields(""),
                    ),
                )
            }
    }

    // ── 2. 게시글 목록 ─────────────────────────────────────────────────────────

    @Test
    fun `게시글 목록 문서화`() {
        val token = signupVerifiedAndToken()
        createPost(token)

        mockMvc
            .get("/community/posts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("offset", "0")
                param("limit", "20")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-post-list",
                        responseSchema = "PostsPageResponse",
                        tag = "Community",
                        summary = "게시글 목록 (고정글 우선 + 최신순, category/search/offset/limit)",
                        responseFields =
                            postResponseFields("posts[].") +
                                listOf(
                                    fieldWithPath("posts").type(JsonFieldType.ARRAY).description("게시글 목록"),
                                    fieldWithPath("hasMore").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                                ),
                    ),
                )
            }
    }

    // ── 3. 게시글 단건 ─────────────────────────────────────────────────────────

    @Test
    fun `게시글 단건 조회 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)

        mockMvc
            .get("/community/posts/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/posts/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-post-get",
                        responseSchema = "PostResponse",
                        tag = "Community",
                        summary = "게시글 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("게시글 ID")),
                        responseFields = postResponseFields(""),
                    ),
                )
            }
    }

    // ── 4. 게시글 수정 ─────────────────────────────────────────────────────────

    @Test
    fun `게시글 수정 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)

        mockMvc
            .patch("/community/posts/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/posts/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("title" to "수정된 제목"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-post-update",
                        requestSchema = "PostUpdateRequest",
                        responseSchema = "PostResponse",
                        tag = "Community",
                        summary = "게시글 수정 (작성자만, 제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("게시글 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("category").type(JsonFieldType.STRING).optional().description("카테고리 변경"),
                                fieldWithPath("title").type(JsonFieldType.STRING).optional().description("제목 변경"),
                                subsectionWithPath("contentJson").type(JsonFieldType.OBJECT).optional().description("본문 변경"),
                                fieldWithPath("contentText").type(JsonFieldType.STRING).optional().description("본문 텍스트 변경"),
                                fieldWithPath("imageUrls").type(JsonFieldType.ARRAY).optional().description("이미지 URL 변경"),
                            ),
                        responseFields = postResponseFields(""),
                    ),
                )
            }
    }

    // ── 5. 게시글 삭제 ─────────────────────────────────────────────────────────

    @Test
    fun `게시글 삭제 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)

        mockMvc
            .delete("/community/posts/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/posts/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-post-delete",
                        tag = "Community",
                        summary = "게시글 삭제 (작성자+관리자, soft delete)",
                        pathParameters = listOf(parameterWithName("id").description("게시글 ID")),
                    ),
                )
            }
    }

    // ── 6. 좋아요 토글 ─────────────────────────────────────────────────────────

    @Test
    fun `좋아요 토글 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)

        mockMvc
            .post("/community/posts/$id/like") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/posts/{id}/like")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-post-like",
                        responseSchema = "LikeToggleResponse",
                        tag = "Community",
                        summary = "좋아요 토글",
                        pathParameters = listOf(parameterWithName("id").description("게시글 ID")),
                        responseFields =
                            listOf(
                                fieldWithPath("liked").type(JsonFieldType.BOOLEAN).description("토글 후 좋아요 여부"),
                                fieldWithPath("likeCount").type(JsonFieldType.NUMBER).description("토글 후 좋아요 수"),
                            ),
                    ),
                )
            }
    }

    // ── 7. 업로드 타깃(presigned) ───────────────────────────────────────────────

    @Test
    fun `업로드 타깃 발급 문서화`() {
        val token = signupVerifiedAndToken()

        mockMvc
            .post("/community/upload-targets") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "files" to
                                listOf(
                                    mapOf("name" to "post.jpg", "type" to "image/jpeg", "size" to 102400),
                                ),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-upload-targets",
                        requestSchema = "CommunityUploadTargetsRequest",
                        responseSchema = "CommunityUploadTargetListResponse",
                        tag = "Community",
                        summary = "이미지 업로드 타깃 발급 (S3 presigned PUT URL). 글 작성 전 업로드해 imageUrls/본문에 사용",
                        requestFields =
                            listOf(
                                fieldWithPath("files").type(JsonFieldType.ARRAY).description("업로드할 파일 메타 목록(필수, 1개 이상)"),
                                fieldWithPath("files[].name").type(JsonFieldType.STRING).description("원본 파일명"),
                                fieldWithPath("files[].type").type(JsonFieldType.STRING).description("MIME 타입(이미지만 허용)"),
                                fieldWithPath("files[].size").type(JsonFieldType.NUMBER).description("파일 크기(byte)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("업로드 타깃 목록"),
                                fieldWithPath("[].uploadUrl").type(JsonFieldType.STRING).description("S3 presigned PUT URL"),
                                fieldWithPath("[].fileUrl").type(JsonFieldType.STRING).description("업로드 후 조회용 CloudFront URL"),
                                fieldWithPath("[].originalName").type(JsonFieldType.STRING).description("원본 파일명"),
                            ),
                    ),
                )
            }
    }

    // ── 8. 댓글 목록 ───────────────────────────────────────────────────────────

    @Test
    fun `댓글 목록 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)
        createComment(token, id)

        mockMvc
            .get("/community/posts/$id/comments") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/posts/{id}/comments")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-comment-list",
                        responseSchema = "CommentListResponse",
                        tag = "Community",
                        summary = "댓글 목록 (대댓글 parentId 포함, 비밀댓글/삭제 마스킹)",
                        pathParameters = listOf(parameterWithName("id").description("게시글 ID")),
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("댓글 목록")) + commentItemFields,
                    ),
                )
            }
    }

    // ── 9. 댓글 작성 ───────────────────────────────────────────────────────────

    @Test
    fun `댓글 작성 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)

        mockMvc
            .post("/community/posts/$id/comments") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/posts/{id}/comments")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("content" to "좋은 정보 감사합니다", "isSecret" to false))
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-comment-create",
                        requestSchema = "CommentCreateRequest",
                        responseSchema = "CommentResponse",
                        tag = "Community",
                        summary = "댓글 작성 (parentId로 대댓글, 부모가 비밀이면 자식도 비밀 강제)",
                        pathParameters = listOf(parameterWithName("id").description("게시글 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("content").type(JsonFieldType.STRING).description("내용 (필수)"),
                                fieldWithPath("parentId").type(JsonFieldType.NUMBER).optional().description("부모 댓글 ID(대댓글일 때)"),
                                fieldWithPath("isSecret").type(JsonFieldType.BOOLEAN).optional().description("비밀댓글 여부(기본 false)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("댓글 ID"),
                                fieldWithPath("postId").type(JsonFieldType.NUMBER).description("게시글 ID"),
                                fieldWithPath("parentId").type(JsonFieldType.NUMBER).optional().description("부모 댓글 ID(없으면 null)"),
                                fieldWithPath("authorNickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
                                fieldWithPath("authorIsAdmin").type(JsonFieldType.BOOLEAN).description("작성자 운영자 여부"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("내용"),
                                fieldWithPath("isSecret").type(JsonFieldType.BOOLEAN).description("비밀댓글 여부"),
                                fieldWithPath("isMine").type(JsonFieldType.BOOLEAN).description("현재 사용자 작성 여부"),
                                fieldWithPath("canView").type(JsonFieldType.BOOLEAN).description("열람 권한"),
                                fieldWithPath("isDeleted").type(JsonFieldType.BOOLEAN).description("삭제 여부"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각(ISO-8601)"),
                            ),
                    ),
                )
            }
    }

    // ── 9-1. 댓글 수정 ─────────────────────────────────────────────────────────

    @Test
    fun `댓글 수정 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)
        val commentId = createComment(token, id)

        mockMvc
            .patch("/community/comments/$commentId") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/comments/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("content" to "내용을 수정했습니다"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-comment-update",
                        requestSchema = "CommentUpdateRequest",
                        responseSchema = "CommentResponse",
                        tag = "Community",
                        summary = "댓글 수정 (작성자 본인만 — 운영자도 불가, 본문만 변경)",
                        pathParameters = listOf(parameterWithName("id").description("댓글 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("content").type(JsonFieldType.STRING).description("수정할 내용 (필수)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("댓글 ID"),
                                fieldWithPath("postId").type(JsonFieldType.NUMBER).description("게시글 ID"),
                                fieldWithPath("parentId").type(JsonFieldType.NUMBER).optional().description("부모 댓글 ID(없으면 null)"),
                                fieldWithPath("authorNickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
                                fieldWithPath("authorIsAdmin").type(JsonFieldType.BOOLEAN).description("작성자 운영자 여부"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("수정된 내용"),
                                fieldWithPath("isSecret").type(JsonFieldType.BOOLEAN).description("비밀댓글 여부"),
                                fieldWithPath("isMine").type(JsonFieldType.BOOLEAN).description("현재 사용자 작성 여부"),
                                fieldWithPath("canView").type(JsonFieldType.BOOLEAN).description("열람 권한"),
                                fieldWithPath("isDeleted").type(JsonFieldType.BOOLEAN).description("삭제 여부"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각(ISO-8601)"),
                            ),
                    ),
                )
            }
    }

    // ── 10. 댓글 삭제 ──────────────────────────────────────────────────────────

    @Test
    fun `댓글 삭제 문서화`() {
        val token = signupVerifiedAndToken()
        val id = createPost(token)
        val commentId = createComment(token, id)

        mockMvc
            .delete("/community/comments/$commentId") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/community/comments/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "community-comment-delete",
                        tag = "Community",
                        summary = "댓글 삭제 (작성자+관리자, soft delete)",
                        pathParameters = listOf(parameterWithName("id").description("댓글 ID")),
                    ),
                )
            }
    }
}
