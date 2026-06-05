package kr.ai.flori.insights.docs

import kr.ai.flori.common.docs.RestDocsSupport
import kr.ai.flori.insights.entity.InstagramPost
import kr.ai.flori.insights.entity.TrendArticle
import kr.ai.flori.insights.repository.InstagramAccountRepository
import kr.ai.flori.insights.repository.InstagramPostRepository
import kr.ai.flori.insights.repository.TrendArticleRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * ScrapController RestDocs 문서화.
 * 스크랩 토글/메모/맵/카운트/목록 — JWT 인증 필요.
 * 트렌드 기사를 리포지토리로 직접 시드해 스크랩 대상을 확보한다.
 */
class ScrapDocsTest : RestDocsSupport() {
    @Autowired
    private lateinit var trendRepository: TrendArticleRepository

    @Autowired
    private lateinit var accountRepository: InstagramAccountRepository

    @Autowired
    private lateinit var postRepository: InstagramPostRepository

    /** InsightScrapResponse 공통 응답 필드 (prefix 없이) */
    private val scrapResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("스크랩 ID"),
            fieldWithPath("targetType").type(JsonFieldType.STRING).description("대상 타입 (trend | post)"),
            fieldWithPath("targetId").type(JsonFieldType.NUMBER).description("대상 ID"),
            fieldWithPath("memo").type(JsonFieldType.STRING).optional().description("메모 (null 가능)"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("스크랩 생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

    /** TrendArticleResponse 항목 필드 — 접두사 없이 */
    private val trendItemFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("트렌드 ID"),
            fieldWithPath("category").type(JsonFieldType.STRING).description("카테고리"),
            fieldWithPath("title").type(JsonFieldType.STRING).description("제목"),
            fieldWithPath("summary").type(JsonFieldType.STRING).description("요약"),
            fieldWithPath("keyPoints").type(JsonFieldType.ARRAY).description("핵심 포인트 목록"),
            fieldWithPath("sourceUrl").type(JsonFieldType.STRING).description("원본 URL"),
            fieldWithPath("sourceName").type(JsonFieldType.STRING).optional().description("출처명 (null 가능)"),
            fieldWithPath("publishedAt").type(JsonFieldType.STRING).optional().description("발행 시각 (ISO-8601, null 가능)"),
            fieldWithPath("collectedAt").type(JsonFieldType.STRING).description("수집일 (yyyy-MM-dd)"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
        )

    /** 트렌드 기사 시드 */
    private fun seedTrend(): Long {
        val article =
            TrendArticle(
                category = "flower",
                title = "스크랩 테스트용 트렌드",
                summary = "스크랩 문서화용 트렌드 요약",
                sourceUrl = "https://scrap.docs.example.com/${UUID.randomUUID()}",
                collectedAt = LocalDate.now(),
            )
        return requireNotNull(trendRepository.save(article).id)
    }

    /** 인스타 포스트 시드 — seed.sql에서 계정이 이미 시드되어 있으므로 첫 번째 계정을 사용 */
    private fun seedPost(): Long {
        val account = accountRepository.findByActiveTrueOrderBySortOrderAscUsernameAsc().first()
        val post =
            InstagramPost(
                accountId = requireNotNull(account.id),
                shortcode = "SCRAP${UUID.randomUUID().toString().take(8)}",
                permalink = "https://www.instagram.com/p/scrap_test/",
                postedAt = Instant.now(),
            ).apply {
                imageUrls = listOf("https://cdn.example.com/scrap-img.jpg")
                caption = "스크랩 문서화용 포스트"
                likeCount = 42
            }
        return requireNotNull(postRepository.save(post).id)
    }

    // ── 1. 스크랩 토글 (추가) ──────────────────────────────────────────────────

    @Test
    fun `스크랩 토글 문서화`() {
        val trendId = seedTrend()
        val token = signupAndToken()

        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "trend", "targetId" to trendId))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "scrap-toggle",
                        requestSchema = "ScrapToggleRequest",
                        responseSchema = "ScrapToggleResponse",
                        tag = "Scraps",
                        summary = "스크랩 토글 (추가이면 true, 해제이면 false)",
                        requestFields =
                            listOf(
                                fieldWithPath("targetType")
                                    .type(JsonFieldType.STRING)
                                    .description("대상 타입 (trend | post, 필수)"),
                                fieldWithPath("targetId")
                                    .type(JsonFieldType.NUMBER)
                                    .description("대상 ID (필수)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("scraped")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("스크랩 결과 — true: 스크랩 추가됨, false: 스크랩 해제됨"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 스크랩 메모 수정 ────────────────────────────────────────────────────

    @Test
    fun `스크랩 메모 수정 문서화`() {
        val trendId = seedTrend()
        val token = signupAndToken()

        // 먼저 스크랩 추가
        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "trend", "targetId" to trendId))
            }.andReturn()

        mockMvc
            .put("/insights/scraps/memo") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "trend", "targetId" to trendId, "memo" to "나중에 다시 읽을 것"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "scrap-memo-update",
                        requestSchema = "ScrapMemoRequest",
                        responseSchema = "InsightScrapResponse",
                        tag = "Scraps",
                        summary = "스크랩 메모 수정 (스크랩 후에만 가능)",
                        requestFields =
                            listOf(
                                fieldWithPath("targetType")
                                    .type(JsonFieldType.STRING)
                                    .description("대상 타입 (trend | post, 필수)"),
                                fieldWithPath("targetId")
                                    .type(JsonFieldType.NUMBER)
                                    .description("대상 ID (필수)"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 내용 (null 이면 삭제)"),
                            ),
                        responseFields = scrapResponseFields,
                    ),
                )
            }
    }

    // ── 3. 스크랩 맵 ──────────────────────────────────────────────────────────

    @Test
    fun `스크랩 맵 문서화`() {
        val trendId = seedTrend()
        val token = signupAndToken()

        // 스크랩 + 메모 설정
        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "trend", "targetId" to trendId))
            }.andReturn()

        mockMvc
            .put("/insights/scraps/memo") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "trend", "targetId" to trendId, "memo" to "메모 예시"))
            }.andReturn()

        mockMvc
            .get("/insights/scraps/map") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("targetType", "trend")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "scrap-map",
                        responseSchema = "ScrapMapResponse",
                        tag = "Scraps",
                        summary = "스크랩 맵 (targetType별 targetId → ScrapInfo 맵)",
                        responseFields =
                            listOf(
                                fieldWithPath("$trendId.id")
                                    .type(JsonFieldType.NUMBER)
                                    .description("스크랩 ID (키는 targetId)"),
                                fieldWithPath("$trendId.memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("메모 (null 가능)"),
                            ),
                    ),
                )
            }
    }

    // ── 4. 스크랩 개수 ────────────────────────────────────────────────────────

    @Test
    fun `스크랩 개수 문서화`() {
        val trendId = seedTrend()
        val token = signupAndToken()

        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "trend", "targetId" to trendId))
            }.andReturn()

        mockMvc
            .get("/insights/scraps/counts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "scrap-counts",
                        responseSchema = "ScrapCountsResponse",
                        tag = "Scraps",
                        summary = "스크랩 개수 (trend/post 별)",
                        responseFields =
                            listOf(
                                fieldWithPath("trend")
                                    .type(JsonFieldType.NUMBER)
                                    .description("트렌드 스크랩 수"),
                                fieldWithPath("post")
                                    .type(JsonFieldType.NUMBER)
                                    .description("포스트 스크랩 수"),
                            ),
                    ),
                )
            }
    }

    // ── 5. 트렌드 스크랩 목록 ─────────────────────────────────────────────────

    @Test
    fun `트렌드 스크랩 목록 문서화`() {
        val trendId = seedTrend()
        val token = signupAndToken()

        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "trend", "targetId" to trendId))
            }.andReturn()

        mockMvc
            .get("/insights/scraps/trends") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("limit", "100")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "scrap-trend-list",
                        responseSchema = "TrendScrapListResponse",
                        tag = "Scraps",
                        summary = "트렌드 스크랩 목록 (스크랩+기사 정보 포함)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("트렌드 스크랩 목록"),
                                fieldWithPath("[].scrap.id").type(JsonFieldType.NUMBER).description("스크랩 ID"),
                                fieldWithPath("[].scrap.targetType").type(JsonFieldType.STRING).description("대상 타입"),
                                fieldWithPath("[].scrap.targetId").type(JsonFieldType.NUMBER).description("대상 ID"),
                                fieldWithPath("[].scrap.memo").type(JsonFieldType.STRING).optional().description("메모 (null 가능)"),
                                fieldWithPath("[].scrap.createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
                                fieldWithPath("[].scrap.updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
                            ) +
                                trendItemFields.map { f ->
                                    fieldWithPath("[].article.${f.path}")
                                        .type(f.type)
                                        .let { d -> if (f.isOptional) d.optional() else d }
                                        .description(f.description as String)
                                },
                    ),
                )
            }
    }

    // ── 6. 포스트 스크랩 목록 ─────────────────────────────────────────────────

    @Test
    fun `포스트 스크랩 목록 문서화`() {
        val postId = seedPost()
        val token = signupAndToken()

        // 포스트 스크랩 추가
        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to "post", "targetId" to postId))
            }.andReturn()

        mockMvc
            .get("/insights/scraps/posts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("limit", "100")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "scrap-post-list",
                        responseSchema = "PostScrapListResponse",
                        tag = "Scraps",
                        summary = "포스트 스크랩 목록 (스크랩+포스트 정보 포함)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("포스트 스크랩 목록"),
                                // scrap 필드 (InsightScrapResponse)
                                fieldWithPath("[].scrap.id").type(JsonFieldType.NUMBER).description("스크랩 ID"),
                                fieldWithPath("[].scrap.targetType").type(JsonFieldType.STRING).description("대상 타입 (post)"),
                                fieldWithPath("[].scrap.targetId").type(JsonFieldType.NUMBER).description("대상 ID"),
                                fieldWithPath("[].scrap.memo").type(JsonFieldType.STRING).optional().description("메모 (null 가능)"),
                                fieldWithPath("[].scrap.createdAt").type(JsonFieldType.STRING).description("스크랩 생성 시각 (ISO-8601)"),
                                fieldWithPath("[].scrap.updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
                                // post 필드 (InstagramPostResponse)
                                fieldWithPath("[].post.id").type(JsonFieldType.NUMBER).description("포스트 ID"),
                                fieldWithPath("[].post.accountId").type(JsonFieldType.NUMBER).description("계정 ID"),
                                fieldWithPath("[].post.shortcode").type(JsonFieldType.STRING).description("인스타그램 shortcode"),
                                fieldWithPath("[].post.permalink").type(JsonFieldType.STRING).description("퍼머링크 URL"),
                                fieldWithPath("[].post.imageUrls").type(JsonFieldType.ARRAY).description("이미지 URL 목록"),
                                fieldWithPath("[].post.caption").type(JsonFieldType.STRING).optional().description("캡션 (null 가능)"),
                                fieldWithPath("[].post.likeCount").type(JsonFieldType.NUMBER).description("좋아요 수"),
                                fieldWithPath("[].post.postedAt").type(JsonFieldType.STRING).description("게시 시각 (ISO-8601)"),
                                fieldWithPath("[].post.account").type(JsonFieldType.OBJECT).optional().description("계정 정보 (null 가능)"),
                                fieldWithPath("[].post.account.id").type(JsonFieldType.NUMBER).optional().description("계정 ID"),
                                fieldWithPath("[].post.account.username").type(JsonFieldType.STRING).optional().description("유저네임"),
                                fieldWithPath(
                                    "[].post.account.displayName",
                                ).type(JsonFieldType.STRING).optional().description("표시명 (null 가능)"),
                                fieldWithPath("[].post.account.profileUrl").type(JsonFieldType.STRING).optional().description("프로필 URL"),
                                fieldWithPath("[].post.account.region").type(JsonFieldType.STRING).optional().description("지역"),
                                fieldWithPath("[].post.account.sortOrder").type(JsonFieldType.NUMBER).optional().description("정렬 순서"),
                                fieldWithPath("[].post.account.active").type(JsonFieldType.BOOLEAN).optional().description("활성 여부"),
                                fieldWithPath("[].post.account.memo").type(JsonFieldType.STRING).optional().description("메모 (null 가능)"),
                            ),
                    ),
                )
            }
    }
}
