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
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * InsightController RestDocs 문서화.
 * 트렌드/인스타 계정/포스트 읽기 — JWT 인증 필요.
 * 공유 데이터(테넌트 무관)이므로 리포지토리로 직접 시드한다.
 */
class InsightDocsTest : RestDocsSupport() {
    @Autowired
    private lateinit var trendRepository: TrendArticleRepository

    @Autowired
    private lateinit var accountRepository: InstagramAccountRepository

    @Autowired
    private lateinit var postRepository: InstagramPostRepository

    /** 트렌드 기사 시드 → 저장된 ID 반환 */
    private fun seedTrend(category: String = "flower"): Long {
        val article =
            TrendArticle(
                category = category,
                title = "플로리스트를 위한 $category 트렌드",
                summary = "$category 봄 시즌 인사이트 요약",
                sourceUrl = "https://seed.docs.example.com/${UUID.randomUUID()}",
                collectedAt = LocalDate.now(),
            ).apply {
                keyPoints = listOf("핵심 포인트1", "핵심 포인트2")
                sourceName = "FlowerTrend"
                publishedAt = Instant.now()
            }
        return requireNotNull(trendRepository.save(article).id)
    }

    /** 인스타 포스트 시드 — seed.sql에서 계정이 이미 시드되어 있으므로 첫 번째 계정을 사용 */
    private fun seedPost(): Long {
        val account = accountRepository.findByActiveTrueOrderBySortOrderAscUsernameAsc().first()
        val post =
            InstagramPost(
                accountId = requireNotNull(account.id),
                shortcode = "DOCS${UUID.randomUUID().toString().take(8)}",
                permalink = "https://www.instagram.com/p/docs_test/",
                postedAt = Instant.now(),
            ).apply {
                imageUrls = listOf("https://cdn.example.com/docs-img.jpg")
                caption = "봄 꽃꽂이 트렌드"
                likeCount = 99
            }
        return requireNotNull(postRepository.save(post).id)
    }

    /** TrendArticleResponse 항목 필드 — 단건 목록용 prefix 없이 */
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

    /** InstagramAccountResponse 필드 */
    private val accountItemFields =
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

    // ── 1. 트렌드 목록 ─────────────────────────────────────────────────────────

    @Test
    fun `트렌드 목록 문서화`() {
        seedTrend("flower")
        val token = signupAndToken()

        mockMvc
            .get("/insights/trends") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("category", "flower")
                param("limit", "50")
                param("offset", "0")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insight-trends-list",
                        tag = "Insights",
                        summary = "트렌드 목록 (category/limit/offset 필터)",
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("트렌드 목록")) +
                                trendItemFields.map { f ->
                                    fieldWithPath("[].${f.path}")
                                        .type(f.type)
                                        .let { d -> if (f.isOptional) d.optional() else d }
                                        .description(f.description as String)
                                },
                    ),
                )
            }
    }

    // ── 2. 카테고리별 최신 트렌드 ──────────────────────────────────────────────

    @Test
    fun `카테고리별 최신 트렌드 문서화`() {
        // InsightService.TREND_CATEGORIES = [flower, inspiration, business, industry]
        // 4개 카테고리 모두 시드해 배열이 비지 않게 한다
        seedTrend("flower")
        seedTrend("inspiration")
        seedTrend("business")
        seedTrend("industry")
        val token = signupAndToken()

        mockMvc
            .get("/insights/trends/recent") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("perCategory", "3")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insight-trends-recent",
                        tag = "Insights",
                        summary = "카테고리별 최신 트렌드 (카테고리명 → TrendArticleResponse[] 맵)",
                        responseFields =
                            buildList {
                                for (cat in listOf("flower", "inspiration", "business", "industry")) {
                                    add(
                                        fieldWithPath(cat)
                                            .type(JsonFieldType.ARRAY)
                                            .description("$cat 카테고리 트렌드 목록"),
                                    )
                                    trendItemFields.forEach { f ->
                                        add(
                                            fieldWithPath("$cat[].${f.path}")
                                                .type(f.type)
                                                .let { d -> if (f.isOptional) d.optional() else d }
                                                .description(f.description as String),
                                        )
                                    }
                                }
                            },
                    ),
                )
            }
    }

    // ── 3. 인스타 계정 목록 ────────────────────────────────────────────────────

    @Test
    fun `인스타 계정 목록 문서화`() {
        // V2 마이그레이션으로 계정이 이미 존재한다
        val token = signupAndToken()

        mockMvc
            .get("/insights/accounts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("activeOnly", "false")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insight-accounts-list",
                        tag = "Insights",
                        summary = "인스타 계정 목록 (activeOnly 필터)",
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("계정 목록")) +
                                accountItemFields.map { f ->
                                    fieldWithPath("[].${f.path}")
                                        .type(f.type)
                                        .let { d -> if (f.isOptional) d.optional() else d }
                                        .description(f.description as String)
                                },
                    ),
                )
            }
    }

    // ── 4. 인스타 포스트 목록 ──────────────────────────────────────────────────

    @Test
    fun `인스타 포스트 목록 문서화`() {
        val postId = seedPost()
        val post = postRepository.findById(postId).orElseThrow()
        val accountId = post.accountId
        val token = signupAndToken()

        mockMvc
            .get("/insights/posts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("accountId", accountId.toString())
                param("sortBy", "latest")
                param("limit", "50")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insight-posts-list",
                        tag = "Insights",
                        summary = "인스타 포스트 목록 (accountId/region/sortBy/daysAgo/limit 필터)",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("포스트 목록"),
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("포스트 ID"),
                                fieldWithPath("[].accountId").type(JsonFieldType.NUMBER).description("계정 ID"),
                                fieldWithPath("[].shortcode").type(JsonFieldType.STRING).description("인스타그램 shortcode"),
                                fieldWithPath("[].permalink").type(JsonFieldType.STRING).description("퍼머링크 URL"),
                                fieldWithPath("[].imageUrls").type(JsonFieldType.ARRAY).description("이미지 URL 목록"),
                                fieldWithPath("[].caption").type(JsonFieldType.STRING).optional().description("캡션 (null 가능)"),
                                fieldWithPath("[].likeCount").type(JsonFieldType.NUMBER).description("좋아요 수"),
                                fieldWithPath("[].postedAt").type(JsonFieldType.STRING).description("게시 시각 (ISO-8601)"),
                                fieldWithPath("[].account").type(JsonFieldType.OBJECT).optional().description("계정 정보 (null 가능)"),
                                fieldWithPath("[].account.id").type(JsonFieldType.NUMBER).optional().description("계정 ID"),
                                fieldWithPath("[].account.username").type(JsonFieldType.STRING).optional().description("유저네임"),
                                fieldWithPath("[].account.displayName").type(JsonFieldType.STRING).optional().description("표시명 (null 가능)"),
                                fieldWithPath("[].account.profileUrl").type(JsonFieldType.STRING).optional().description("프로필 URL"),
                                fieldWithPath("[].account.region").type(JsonFieldType.STRING).optional().description("지역"),
                                fieldWithPath("[].account.sortOrder").type(JsonFieldType.NUMBER).optional().description("정렬 순서"),
                                fieldWithPath("[].account.active").type(JsonFieldType.BOOLEAN).optional().description("활성 여부"),
                                fieldWithPath("[].account.notes").type(JsonFieldType.STRING).optional().description("메모 (null 가능)"),
                            ),
                    ),
                )
            }
    }
}
