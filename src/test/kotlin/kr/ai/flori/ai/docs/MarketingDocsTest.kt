package kr.ai.flori.ai.docs

import kr.ai.flori.ai.client.AiBlogCall
import kr.ai.flori.ai.client.AiBlogDraft
import kr.ai.flori.ai.client.AiBlogFaq
import kr.ai.flori.ai.client.AiBlogResult
import kr.ai.flori.ai.client.AiBlogSection
import kr.ai.flori.ai.client.AiServerClient
import kr.ai.flori.ai.entity.AiMarketingContent
import kr.ai.flori.ai.repository.AiMarketingContentRepository
import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * AI 마케팅(/ai/marketing 하위) RestDocs 문서화. ai-server HTTP 호출([AiServerClient])만 스텁하고,
 * tone_profile·콘텐츠 영속은 실제 Zonky PG에서 수행한다.
 */
class MarketingDocsTest : RestDocsSupport() {
    @MockitoBean
    private lateinit var aiClient: AiServerClient

    @Autowired
    private lateinit var contentRepository: AiMarketingContentRepository

    // 비-널 Kotlin 파라미터용 any() 매처(mockito-kotlin 미사용). Mockito.any로 매처를 등록한 뒤
    // 제네릭 unchecked 캐스트로 null을 반환한다(제네릭 T라 Kotlin이 런타임 널 체크를 넣지 않음 → NPE 없음).
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun anyBlogCall(): AiBlogCall {
        Mockito.any(AiBlogCall::class.java)
        return uninitialized()
    }

    private fun stubBlog() {
        Mockito
            .`when`(aiClient.generateBlog(anyString(), anyLong(), anyBlogCall()))
            .thenReturn(
                AiBlogResult(
                    draft =
                        AiBlogDraft(
                            title = "어버이날 카네이션 꽃다발 추천",
                            sections = listOf(AiBlogSection("어떤 꽃이 좋을까요?", "카네이션이 무난합니다.")),
                            faq = listOf(AiBlogFaq("당일배송 되나요?", "네 가능합니다.")),
                            hashtags = listOf("#어버이날꽃", "#카네이션"),
                        ),
                    model = "claude-haiku-4-5",
                ),
            )
    }

    @Test
    fun `블로그 초안 생성 문서화`() {
        val token = signupAndToken()
        stubBlog()

        mockMvc
            .post("/ai/marketing/blog") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "keyword" to "어버이날 카네이션 꽃다발",
                            "situation" to "어버이날",
                            "memo" to "비누꽃도 추천",
                            "photoUrls" to listOf("https://cdn.example.com/photos/a.jpg"),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-marketing-blog",
                        requestSchema = "BlogGenerateRequest",
                        responseSchema = "BlogGenerateResponse",
                        tag = "AI 마케팅",
                        summary = "사진+키워드 → 네이버 GEO 최적화 블로그 초안 생성(말투·매장맥락 자동 주입)",
                        requestFields =
                            listOf(
                                fieldWithPath("keyword").type(JsonFieldType.STRING).description("타깃 키워드(필수, 최대 200자)"),
                                fieldWithPath("situation").type(JsonFieldType.STRING).optional().description("상황(선택, 최대 100자)"),
                                fieldWithPath("memo").type(JsonFieldType.STRING).optional().description("메모(선택, 최대 500자)"),
                                fieldWithPath("photoUrls").type(JsonFieldType.ARRAY).optional().description("사진 URL(선택, 0~4장)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("contentId").type(JsonFieldType.STRING).description("저장된 콘텐츠 ID(상세/복사/삭제용)"),
                                fieldWithPath("draft.title").type(JsonFieldType.STRING).description("제목"),
                                fieldWithPath("draft.sections").type(JsonFieldType.ARRAY).description("소제목 단락"),
                                fieldWithPath("draft.sections[].heading").type(JsonFieldType.STRING).description("소제목"),
                                fieldWithPath("draft.sections[].body").type(JsonFieldType.STRING).description("단락 본문"),
                                fieldWithPath("draft.faq").type(JsonFieldType.ARRAY).description("FAQ"),
                                fieldWithPath("draft.faq[].q").type(JsonFieldType.STRING).description("질문"),
                                fieldWithPath("draft.faq[].a").type(JsonFieldType.STRING).description("답변"),
                                fieldWithPath("draft.hashtags").type(JsonFieldType.ARRAY).description("해시태그"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `말투 프로필 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/ai/marketing/tone-profile") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-marketing-tone-profile-get",
                        responseSchema = "ToneProfileResponse",
                        tag = "AI 마케팅",
                        summary = "말투 프로필 조회(샘플 0~3개)",
                        responseFields =
                            listOf(
                                fieldWithPath("samples").type(JsonFieldType.ARRAY).description("말투 샘플 목록"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `말투 프로필 수정 문서화`() {
        val token = signupAndToken()

        mockMvc
            .put("/ai/marketing/tone-profile") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("samples" to listOf("안녕하세요 사장입니다.", "오늘도 예쁜 꽃 준비했어요.")))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-marketing-tone-profile-put",
                        requestSchema = "ToneProfileUpdateRequest",
                        responseSchema = "ToneProfileResponse",
                        tag = "AI 마케팅",
                        summary = "말투 프로필 수정(upsert, 샘플 최대 3개)",
                        requestFields =
                            listOf(
                                fieldWithPath("samples").type(JsonFieldType.ARRAY).optional().description("말투 샘플(최대 3개, 각 최대 4000자)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("samples").type(JsonFieldType.ARRAY).description("저장된 말투 샘플"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `마케팅 콘텐츠 목록 문서화`() {
        val token = signupAndToken()
        seedContent(token)

        mockMvc
            .get("/ai/marketing/contents?channel=blog&offset=0&limit=20") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-marketing-contents",
                        responseSchema = "MarketingContentsResponse",
                        tag = "AI 마케팅",
                        summary = "생성한 마케팅 콘텐츠 목록(소프트삭제 제외, 최신순)",
                        responseFields =
                            listOf(
                                fieldWithPath("contents").type(JsonFieldType.ARRAY).description("콘텐츠 목록"),
                                fieldWithPath("contents[].id").type(JsonFieldType.STRING).description("콘텐츠 ID"),
                                fieldWithPath("contents[].channel").type(JsonFieldType.STRING).description("채널(blog)"),
                                fieldWithPath("contents[].title").type(JsonFieldType.STRING).description("초안 제목"),
                                fieldWithPath("contents[].keyword").type(JsonFieldType.STRING).description("생성에 사용한 타깃 키워드"),
                                fieldWithPath("contents[].createdAt").type(JsonFieldType.STRING).description("생성 시각(ISO-8601)"),
                                fieldWithPath("hasMore").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `마케팅 콘텐츠 상세 문서화`() {
        val token = signupAndToken()
        val id = seedContent(token)

        mockMvc
            .get("/ai/marketing/contents/{id}", id) {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/ai/marketing/contents/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-marketing-content-detail",
                        responseSchema = "MarketingContentDetail",
                        tag = "AI 마케팅",
                        summary = "마케팅 콘텐츠 상세(초안 포함)",
                        pathParameters = listOf(parameterWithName("id").description("콘텐츠 ID")),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.STRING).description("콘텐츠 ID"),
                                fieldWithPath("channel").type(JsonFieldType.STRING).description("채널(blog)"),
                                fieldWithPath("title").type(JsonFieldType.STRING).description("초안 제목"),
                                fieldWithPath("keyword").type(JsonFieldType.STRING).description("타깃 키워드"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각(ISO-8601)"),
                                fieldWithPath("situation").type(JsonFieldType.STRING).optional().description("상황/시즌"),
                                fieldWithPath("memo").type(JsonFieldType.STRING).optional().description("사장님 메모"),
                                fieldWithPath("photoUrls").type(JsonFieldType.ARRAY).description("사진 URL 목록"),
                                fieldWithPath("draft.title").type(JsonFieldType.STRING).description("제목"),
                                fieldWithPath("draft.sections").type(JsonFieldType.ARRAY).description("소제목 단락"),
                                fieldWithPath("draft.sections[].heading").type(JsonFieldType.STRING).description("소제목"),
                                fieldWithPath("draft.sections[].body").type(JsonFieldType.STRING).description("단락 본문"),
                                fieldWithPath("draft.faq").type(JsonFieldType.ARRAY).description("FAQ"),
                                fieldWithPath("draft.faq[].q").type(JsonFieldType.STRING).description("질문"),
                                fieldWithPath("draft.faq[].a").type(JsonFieldType.STRING).description("답변"),
                                fieldWithPath("draft.hashtags").type(JsonFieldType.ARRAY).description("해시태그"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `마케팅 콘텐츠 소프트삭제 문서화`() {
        val token = signupAndToken()
        val id = seedContent(token)

        mockMvc
            .delete("/ai/marketing/contents/{id}", id) {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/ai/marketing/contents/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-marketing-content-delete",
                        tag = "AI 마케팅",
                        summary = "마케팅 콘텐츠 소프트삭제",
                        pathParameters = listOf(parameterWithName("id").description("콘텐츠 ID")),
                    ),
                )
            }
    }

    @Test
    fun `마케팅 콘텐츠 수정 문서화`() {
        val token = signupAndToken()
        val id = seedContent(token)

        mockMvc
            .put("/ai/marketing/contents/{id}", id) {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/ai/marketing/contents/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "title" to "어버이날 카네이션 꽃다발 추천(수정)",
                            "sections" to listOf(mapOf("heading" to "고른 이유", "body" to "오래가고 무난합니다.")),
                            "faq" to listOf(mapOf("q" to "당일배송 되나요?", "a" to "네 가능합니다.")),
                            "hashtags" to listOf("#어버이날꽃", "#카네이션"),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-marketing-content-update",
                        requestSchema = "MarketingContentUpdateRequest",
                        responseSchema = "MarketingContentDetail",
                        tag = "AI 마케팅",
                        summary = "마케팅 콘텐츠(블로그 초안) 수정 — output(초안)만 갱신, 입력 메타는 불변",
                        pathParameters = listOf(parameterWithName("id").description("콘텐츠 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("title").type(JsonFieldType.STRING).description("제목(필수, 최대 300자)"),
                                fieldWithPath("sections").type(JsonFieldType.ARRAY).description("본문 단락(1~30개)"),
                                fieldWithPath("sections[].heading").type(JsonFieldType.STRING).description("소제목(최대 300자)"),
                                fieldWithPath("sections[].body").type(JsonFieldType.STRING).description("단락 본문(최대 10000자)"),
                                fieldWithPath("faq").type(JsonFieldType.ARRAY).optional().description("FAQ(최대 30개)"),
                                fieldWithPath("faq[].q").type(JsonFieldType.STRING).description("질문(최대 1000자)"),
                                fieldWithPath("faq[].a").type(JsonFieldType.STRING).description("답변(최대 4000자)"),
                                fieldWithPath("hashtags").type(JsonFieldType.ARRAY).optional().description("해시태그(최대 30개, 각 최대 100자)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("id").type(JsonFieldType.STRING).description("콘텐츠 ID"),
                                fieldWithPath("channel").type(JsonFieldType.STRING).description("채널(blog)"),
                                fieldWithPath("title").type(JsonFieldType.STRING).description("수정된 초안 제목"),
                                fieldWithPath("keyword").type(JsonFieldType.STRING).description("타깃 키워드(불변)"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각(ISO-8601, 불변)"),
                                fieldWithPath("situation").type(JsonFieldType.STRING).optional().description("상황/시즌(불변)"),
                                fieldWithPath("memo").type(JsonFieldType.STRING).optional().description("사장님 메모(불변)"),
                                fieldWithPath("photoUrls").type(JsonFieldType.ARRAY).description("사진 URL 목록(불변)"),
                                fieldWithPath("draft.title").type(JsonFieldType.STRING).description("제목"),
                                fieldWithPath("draft.sections").type(JsonFieldType.ARRAY).description("소제목 단락"),
                                fieldWithPath("draft.sections[].heading").type(JsonFieldType.STRING).description("소제목"),
                                fieldWithPath("draft.sections[].body").type(JsonFieldType.STRING).description("단락 본문"),
                                fieldWithPath("draft.faq").type(JsonFieldType.ARRAY).description("FAQ"),
                                fieldWithPath("draft.faq[].q").type(JsonFieldType.STRING).description("질문"),
                                fieldWithPath("draft.faq[].a").type(JsonFieldType.STRING).description("답변"),
                                fieldWithPath("draft.hashtags").type(JsonFieldType.ARRAY).description("해시태그"),
                            ),
                    ),
                )
            }
    }

    /** 문서화용 콘텐츠 1건을 시드하고 id를 반환한다. 입력/출력 JSON을 모두 채워 상세 응답 필드가 결정적이게 한다. */
    private fun seedContent(token: String): Long {
        val userId = tokenProvider.parse(token)!!.userId
        val input =
            mapOf(
                "keyword" to "어버이날 카네이션 꽃다발",
                "situation" to "어버이날",
                "memo" to "비누꽃도 추천",
                "photoUrls" to listOf("https://cdn.example.com/photos/a.jpg"),
            )
        val draft =
            mapOf(
                "title" to "어버이날 카네이션 꽃다발 추천",
                "sections" to listOf(mapOf("heading" to "어떤 꽃이 좋을까요?", "body" to "카네이션이 무난합니다.")),
                "faq" to listOf(mapOf("q" to "당일배송 되나요?", "a" to "네 가능합니다.")),
                "hashtags" to listOf("#어버이날꽃", "#카네이션"),
            )
        return contentRepository
            .save(
                AiMarketingContent(userId, "blog").apply {
                    inputJson = objectMapper.valueToTree(input)
                    outputJson = objectMapper.valueToTree(draft)
                },
            ).id!!
    }
}
