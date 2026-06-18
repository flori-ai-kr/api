package kr.ai.flori.insights.docs

import kr.ai.flori.common.docs.RestDocsSupport
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.insights.domain.GrantCategories
import kr.ai.flori.insights.domain.ScrapTargetTypes
import kr.ai.flori.insights.domain.TrendCategories
import kr.ai.flori.insights.entity.FlowerAuctionPrice
import kr.ai.flori.insights.entity.SupportProgram
import kr.ai.flori.insights.entity.TrendArticle
import kr.ai.flori.insights.repository.FlowerAuctionPriceRepository
import kr.ai.flori.insights.repository.InsightScrapRepository
import kr.ai.flori.insights.repository.SupportProgramRepository
import kr.ai.flori.insights.repository.TrendArticleRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 인사이트 API RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 호출하며 OpenAPI를 생성한다.
 * 공유 읽기 + 개인 스크랩 모두 빈 배열/빈 맵 매칭 실패를 막기 위해 데이터를 시드한 뒤 호출한다.
 */
class InsightDocsTest : RestDocsSupport() {
    @Autowired
    lateinit var trendRepository: TrendArticleRepository

    @Autowired
    lateinit var priceRepository: FlowerAuctionPriceRepository

    @Autowired
    lateinit var programRepository: SupportProgramRepository

    @Autowired
    lateinit var scrapRepository: InsightScrapRepository

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
        scrapRepository.deleteAll()
        trendRepository.deleteAll()
        priceRepository.deleteAll()
        programRepository.deleteAll()
    }

    // ── 시드 헬퍼 ─────────────────────────────────────────────────────────

    private fun seedTrend(): Long {
        val a =
            TrendArticle(
                category = TrendCategories.FLOWER,
                title = "2026 여름 웨딩 꽃 트렌드",
                summary = "파스텔 톤 라넌큘러스가 인기",
                sourceUrl = "https://example.com/${UUID.randomUUID()}",
                collectedAt = LocalDate.of(2026, 6, 17),
            ).apply {
                keyPoints = listOf("라넌큘러스", "파스텔")
                sourceName = "플로리스트 매거진"
                publishedAt = Instant.parse("2026-06-17T00:00:00Z")
            }
        return requireNotNull(trendRepository.save(a).id)
    }

    private fun seedGrant(): Long {
        val p =
            SupportProgram(source = "sbiz", sourceId = UUID.randomUUID().toString(), title = "소상공인 마케팅 지원").apply {
                category = GrantCategories.MARKETING
                agency = "소상공인시장진흥공단"
                target = "소상공인"
                summary = "온라인 마케팅 비용 지원"
                applyStart = LocalDate.of(2026, 6, 1)
                applyEnd = LocalDate.of(2026, 7, 31)
                sourceUrl = "https://example.com/${UUID.randomUUID()}"
            }
        return requireNotNull(programRepository.save(p).id)
    }

    private fun seedAuction() {
        priceRepository.save(auctionRow(LocalDate.of(2026, 6, 16), 1000))
        priceRepository.save(auctionRow(LocalDate.of(2026, 6, 17), 1100))
    }

    private fun auctionRow(
        date: LocalDate,
        avg: Int,
    ): FlowerAuctionPrice =
        FlowerAuctionPrice(saleDate = date, flowerGubn = "절화", pumName = "거베라").apply {
            goodName = "미니(혼합)"
            lvNm = "특2"
            avgAmt = avg
            maxAmt = avg + 1000
            minAmt = avg - 500
            totQty = 3952L
            totAmt = avg.toLong() * 100
        }

    // ── 응답 필드 ─────────────────────────────────────────────────────────

    private fun trendItemFields(prefix: String): List<FieldDescriptor> =
        listOf(
            fieldWithPath("${prefix}id").type(JsonFieldType.NUMBER).description("기사 ID"),
            fieldWithPath("${prefix}category").type(JsonFieldType.STRING).description("flower|inspiration|business|industry"),
            fieldWithPath("${prefix}title").type(JsonFieldType.STRING).description("제목"),
            fieldWithPath("${prefix}summary").type(JsonFieldType.STRING).description("요약"),
            fieldWithPath("${prefix}keyPoints").type(JsonFieldType.ARRAY).description("핵심 요점(문자열 배열)"),
            fieldWithPath("${prefix}sourceUrl").type(JsonFieldType.STRING).description("원문 URL"),
            fieldWithPath("${prefix}sourceName").type(JsonFieldType.STRING).optional().description("출처명(없으면 null)"),
            fieldWithPath("${prefix}publishedAt").type(JsonFieldType.STRING).optional().description("원문 발행 시각(ISO-8601, 없으면 null)"),
            fieldWithPath("${prefix}collectedAt").type(JsonFieldType.STRING).description("수집일(yyyy-MM-dd)"),
            fieldWithPath("${prefix}createdAt").type(JsonFieldType.STRING).description("적재 시각(ISO-8601)"),
        )

    private fun grantItemFields(prefix: String): List<FieldDescriptor> =
        listOf(
            fieldWithPath("${prefix}id").type(JsonFieldType.NUMBER).description("지원사업 ID"),
            fieldWithPath("${prefix}source").type(JsonFieldType.STRING).description("출처(소진공/기업마당/K-Startup 등)"),
            fieldWithPath("${prefix}title").type(JsonFieldType.STRING).description("사업명"),
            fieldWithPath("${prefix}agency").type(JsonFieldType.STRING).optional().description("주관기관"),
            fieldWithPath("${prefix}category").type(JsonFieldType.STRING).optional().description("fund|marketing|education(없으면 null)"),
            fieldWithPath("${prefix}target").type(JsonFieldType.STRING).optional().description("지원 대상"),
            fieldWithPath("${prefix}summary").type(JsonFieldType.STRING).optional().description("요약"),
            fieldWithPath("${prefix}applyStart").type(JsonFieldType.STRING).optional().description("접수 시작일(yyyy-MM-dd)"),
            fieldWithPath("${prefix}applyEnd").type(JsonFieldType.STRING).optional().description("접수 마감일(yyyy-MM-dd)"),
            fieldWithPath("${prefix}sourceUrl").type(JsonFieldType.STRING).optional().description("원문 URL"),
            fieldWithPath("${prefix}dDay").type(JsonFieldType.NUMBER).optional().description("마감까지 남은 일수(서버 파생, 마감 없음/지남이면 null/음수)"),
            fieldWithPath("${prefix}createdAt").type(JsonFieldType.STRING).description("적재 시각(ISO-8601)"),
        )

    private fun scrapFields(prefix: String): List<FieldDescriptor> =
        listOf(
            fieldWithPath("${prefix}id").type(JsonFieldType.NUMBER).description("스크랩 ID"),
            fieldWithPath("${prefix}targetType").type(JsonFieldType.STRING).description("trend|grant"),
            fieldWithPath("${prefix}targetId").type(JsonFieldType.NUMBER).description("대상 ID(간접참조)"),
            fieldWithPath("${prefix}memo").type(JsonFieldType.STRING).optional().description("메모(없으면 null)"),
            fieldWithPath("${prefix}createdAt").type(JsonFieldType.STRING).description("스크랩 시각(ISO-8601)"),
            fieldWithPath("${prefix}updatedAt").type(JsonFieldType.STRING).description("수정 시각(ISO-8601)"),
        )

    // ── 1. 트렌드 목록 ────────────────────────────────────────────────────

    @Test
    fun `트렌드 목록 문서화`() {
        val token = signupAndToken()
        seedTrend()

        mockMvc
            .get("/insights/trends") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("offset", "0")
                param("limit", "50")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-trends-list",
                        responseSchema = "TrendArticleListResponse",
                        tag = "Insights",
                        summary = "트렌드·뉴스 목록 (category|offset|limit, 수집일 최신순)",
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("트렌드 기사 목록")) +
                                trendItemFields("[]."),
                    ),
                )
            }
    }

    // ── 2. 트렌드 최신(카테고리별) ─────────────────────────────────────────

    @Test
    fun `트렌드 카테고리별 최신 문서화`() {
        val token = signupAndToken()
        seedTrend()

        mockMvc
            .get("/insights/trends/recent") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("perCategory", "3")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-trends-recent",
                        responseSchema = "TrendsByCategoryResponse",
                        tag = "Insights",
                        summary = "카테고리별 최신 N개 묶음 (키: flower/inspiration/business/industry → 기사 배열)",
                        responseFields =
                            listOf(
                                subsectionWithPath("flower").type(JsonFieldType.ARRAY).description("꽃 트렌드 기사 배열"),
                                subsectionWithPath("inspiration").type(JsonFieldType.ARRAY).description("영감 기사 배열"),
                                subsectionWithPath("business").type(JsonFieldType.ARRAY).description("사업 트렌드 기사 배열"),
                                subsectionWithPath("industry").type(JsonFieldType.ARRAY).description("업계 뉴스 기사 배열"),
                            ),
                    ),
                )
            }
    }

    // ── 3. 트렌드 카운트 ──────────────────────────────────────────────────

    @Test
    fun `트렌드 카테고리별 카운트 문서화`() {
        val token = signupAndToken()
        seedTrend()

        mockMvc
            .get("/insights/trends/counts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-trends-counts",
                        responseSchema = "TrendCountsResponse",
                        tag = "Insights",
                        summary = "카테고리별 기사 수 (키: 카테고리 → 건수). since(yyyy-MM-dd) 옵션",
                        responseFields =
                            listOf(
                                fieldWithPath("flower").type(JsonFieldType.NUMBER).description("꽃 트렌드 건수"),
                                fieldWithPath("inspiration").type(JsonFieldType.NUMBER).description("영감 건수"),
                                fieldWithPath("business").type(JsonFieldType.NUMBER).description("사업 트렌드 건수"),
                                fieldWithPath("industry").type(JsonFieldType.NUMBER).description("업계 뉴스 건수"),
                            ),
                    ),
                )
            }
    }

    // ── 4. 화훼 카테고리 목록 ─────────────────────────────────────────────

    @Test
    fun `화훼 카테고리 목록 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/insights/auction/categories") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-auction-categories",
                        responseSchema = "FlowerCategoryListResponse",
                        tag = "Insights",
                        summary = "화훼 경매 카테고리 4종 (절화/관엽/난/춘란). code 는 시세 조회 gubn 필터값",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("카테고리 목록(4종 고정)"),
                                fieldWithPath("[].code").type(JsonFieldType.STRING).description("코드(1=절화 2=관엽 3=난 4=춘란). f001 flowerGubn"),
                                fieldWithPath("[].label").type(JsonFieldType.STRING).description("표시 라벨(응답 flower_gubn 텍스트)"),
                            ),
                    ),
                )
            }
    }

    // ── 5a. 경매 정산일 목록 ──────────────────────────────────────────────

    @Test
    fun `경매 정산일 목록 문서화`() {
        val token = signupAndToken()
        seedAuction()

        mockMvc
            .get("/insights/auction/dates") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("gubn", "절화")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-auction-dates",
                        responseSchema = "AuctionDatesResponse",
                        tag = "Insights",
                        summary = "경매 정산일 목록 (date picker용, 최신순 distinct ~60). gubn 옵션 필터",
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("정산일자 목록(yyyy-MM-dd, 최신순)"),
                            ),
                    ),
                )
            }
    }

    // ── 5b. 경매 요약(품목 단위) ──────────────────────────────────────────

    @Test
    fun `경매 요약 문서화`() {
        val token = signupAndToken()
        seedAuction()

        mockMvc
            .get("/insights/auction/summary") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("gubn", "절화")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-auction-summary",
                        responseSchema = "AuctionSummaryResponse",
                        tag = "Insights",
                        summary =
                            "경매 요약 (품목 단위. date 미지정 시 완전한 최신 정산일. 가중평균·등락 방식 A 중앙값). date|gubn 필터",
                        responseFields =
                            listOf(
                                fieldWithPath(
                                    "date",
                                ).type(JsonFieldType.STRING).optional().description("기준 정산일자(yyyy-MM-dd, 완전한 최신일. 데이터 없으면 null)"),
                                fieldWithPath("source").type(JsonFieldType.STRING).description("출처(이용허락범위 제작자 표시). 화훼유통정보(aT)"),
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("품목(pum_name) 요약 목록(거래량 많은 순)"),
                                fieldWithPath("items[].pumName").type(JsonFieldType.STRING).description("품목명"),
                                fieldWithPath(
                                    "items[].repAvg",
                                ).type(JsonFieldType.NUMBER).optional().description("대표 평균가(거래량 가중평균, 원. 거래량 없으면 null)"),
                                fieldWithPath(
                                    "items[].repChangeRate",
                                ).type(JsonFieldType.NUMBER).optional().description(
                                    "대표 등락률(방식 A=매칭 품종·등급 등락률 중앙값, 비율. 매칭 변형 없으면 null)",
                                ),
                                fieldWithPath(
                                    "items[].variantCount",
                                ).type(JsonFieldType.NUMBER).description("그 품목·그날의 품종·등급 행 수"),
                            ),
                    ),
                )
            }
    }

    // ── 5. 경매 시세(드릴다운) ────────────────────────────────────────────

    @Test
    fun `경매 시세 문서화`() {
        val token = signupAndToken()
        seedAuction()

        mockMvc
            .get("/insights/auction/prices") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("gubn", "절화")
                param("item", "거베라")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-auction-prices",
                        responseSchema = "AuctionPricesResponse",
                        tag = "Insights",
                        summary =
                            "경매 시세 드릴다운 (단일 시장 aT 양재. date 미지정 시 완전한 최신 정산일). " +
                                "직전 정산일 대비 등락률 파생. date|gubn|item(품목) 필터",
                        responseFields =
                            listOf(
                                fieldWithPath(
                                    "date",
                                ).type(JsonFieldType.STRING).optional().description("기준 정산일자(yyyy-MM-dd, 데이터 없으면 null)"),
                                fieldWithPath("source").type(JsonFieldType.STRING).description("출처(이용허락범위 제작자 표시). 화훼유통정보(aT)"),
                                fieldWithPath("prices").type(JsonFieldType.ARRAY).description("시세 행 목록"),
                                fieldWithPath("prices[].flowerGubn").type(JsonFieldType.STRING).description("화훼 구분(절화/관엽/난/춘란)"),
                                fieldWithPath("prices[].pumName").type(JsonFieldType.STRING).description("품목명"),
                                fieldWithPath("prices[].goodName").type(JsonFieldType.STRING).description("품종명"),
                                fieldWithPath("prices[].lvNm").type(JsonFieldType.STRING).description("등급명(특2/상1/보3 …)"),
                                fieldWithPath("prices[].avgAmt").type(JsonFieldType.NUMBER).optional().description("평균 단가(원)"),
                                fieldWithPath("prices[].maxAmt").type(JsonFieldType.NUMBER).optional().description("최고 단가(원)"),
                                fieldWithPath("prices[].minAmt").type(JsonFieldType.NUMBER).optional().description("최저 단가(원)"),
                                fieldWithPath("prices[].totQty").type(JsonFieldType.NUMBER).optional().description("총 거래량"),
                                fieldWithPath("prices[].totAmt").type(JsonFieldType.NUMBER).optional().description("총 거래금액(원)"),
                                fieldWithPath(
                                    "prices[].prevAvgAmt",
                                ).type(JsonFieldType.NUMBER).optional().description("직전 정산일자 평균가(파생, 없으면 null)"),
                                fieldWithPath(
                                    "prices[].changeRate",
                                ).type(JsonFieldType.NUMBER).optional().description("등락률(파생 비율, 예 0.05=+5%. 직전값 없으면 null)"),
                            ),
                    ),
                )
            }
    }

    // ── 6. 지원사업 목록 ──────────────────────────────────────────────────

    @Test
    fun `지원사업 목록 문서화`() {
        val token = signupAndToken()
        seedGrant()

        mockMvc
            .get("/insights/grants") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("offset", "0")
                param("limit", "50")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-grants-list",
                        responseSchema = "SupportProgramListResponse",
                        tag = "Insights",
                        summary = "지원사업 목록 (category|offset|limit, 마감 임박순 + dDay 파생)",
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("지원사업 목록")) +
                                grantItemFields("[]."),
                    ),
                )
            }
    }

    // ── 7. 스크랩 토글 ────────────────────────────────────────────────────

    @Test
    fun `스크랩 토글 문서화`() {
        val token = signupAndToken()
        val articleId = seedTrend()

        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to ScrapTargetTypes.TREND, "targetId" to articleId))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-scrap-toggle",
                        requestSchema = "ScrapToggleRequest",
                        responseSchema = "ScrapToggleResponse",
                        tag = "Insights",
                        summary = "스크랩 토글 (멱등: 없으면 추가 true, 있으면 해제 false)",
                        requestFields =
                            listOf(
                                fieldWithPath("targetType").type(JsonFieldType.STRING).description("trend|grant (필수)"),
                                fieldWithPath("targetId").type(JsonFieldType.NUMBER).description("대상 ID (필수)"),
                            ),
                        responseFields =
                            listOf(fieldWithPath("scraped").type(JsonFieldType.BOOLEAN).description("토글 후 스크랩 여부")),
                    ),
                )
            }
    }

    // ── 8. 스크랩 메모 ────────────────────────────────────────────────────

    @Test
    fun `스크랩 메모 수정 문서화`() {
        val token = signupAndToken()
        val articleId = seedTrend()

        mockMvc
            .put("/insights/scraps/memo") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(mapOf("targetType" to ScrapTargetTypes.TREND, "targetId" to articleId, "memo" to "다음 시즌 참고"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-scrap-memo",
                        requestSchema = "ScrapMemoRequest",
                        responseSchema = "ScrapResponse",
                        tag = "Insights",
                        summary = "스크랩 메모 수정 (아직 스크랩 안 했으면 메모와 함께 생성, upsert)",
                        requestFields =
                            listOf(
                                fieldWithPath("targetType").type(JsonFieldType.STRING).description("trend|grant (필수)"),
                                fieldWithPath("targetId").type(JsonFieldType.NUMBER).description("대상 ID (필수)"),
                                fieldWithPath("memo").type(JsonFieldType.STRING).optional().description("메모(null/공백이면 메모 비움)"),
                            ),
                        responseFields = scrapFields(""),
                    ),
                )
            }
    }

    // ── 9. 스크랩 맵 ──────────────────────────────────────────────────────

    @Test
    fun `스크랩 맵 문서화`() {
        val token = signupAndToken()
        val articleId = seedTrend()
        toggle(token, ScrapTargetTypes.TREND, articleId)

        mockMvc
            .get("/insights/scraps/map") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("targetType", ScrapTargetTypes.TREND)
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-scrap-map",
                        responseSchema = "ScrapMapResponse",
                        tag = "Insights",
                        summary = "스크랩 맵 (targetType별, 키=targetId 문자열 → {id, memo}). 목록 화면 스크랩 표시용",
                        responseFields =
                            listOf(
                                subsectionWithPath("*").type(JsonFieldType.OBJECT).description("키=targetId(문자열), 값={id, memo}"),
                            ),
                    ),
                )
            }
    }

    // ── 10. 트렌드 스크랩 목록 ────────────────────────────────────────────

    @Test
    fun `트렌드 스크랩 목록 문서화`() {
        val token = signupAndToken()
        val articleId = seedTrend()
        toggle(token, ScrapTargetTypes.TREND, articleId)

        mockMvc
            .get("/insights/scraps/trends") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("limit", "100")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-scrap-trends",
                        responseSchema = "TrendScrapListResponse",
                        tag = "Insights",
                        summary = "내 트렌드 스크랩 목록 (스크랩 + 대상 기사 조인, 최신순)",
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("트렌드 스크랩 목록")) +
                                scrapFields("[].scrap.") +
                                trendItemFields("[].article."),
                    ),
                )
            }
    }

    // ── 11. 지원사업 스크랩 목록 ──────────────────────────────────────────

    @Test
    fun `지원사업 스크랩 목록 문서화`() {
        val token = signupAndToken()
        val grantId = seedGrant()
        toggle(token, ScrapTargetTypes.GRANT, grantId)

        mockMvc
            .get("/insights/scraps/grants") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("limit", "100")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-scrap-grants",
                        responseSchema = "GrantScrapListResponse",
                        tag = "Insights",
                        summary = "내 지원사업 스크랩 목록 (스크랩 + 대상 사업 조인, 최신순)",
                        responseFields =
                            listOf(fieldWithPath("[]").type(JsonFieldType.ARRAY).description("지원사업 스크랩 목록")) +
                                scrapFields("[].scrap.") +
                                grantItemFields("[].program."),
                    ),
                )
            }
    }

    // ── 12. 스크랩 카운트 ─────────────────────────────────────────────────

    @Test
    fun `스크랩 카운트 문서화`() {
        val token = signupAndToken()
        val articleId = seedTrend()
        toggle(token, ScrapTargetTypes.TREND, articleId)

        mockMvc
            .get("/insights/scraps/counts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "insights-scrap-counts",
                        responseSchema = "ScrapCountsResponse",
                        tag = "Insights",
                        summary = "대상 유형별 스크랩 수",
                        responseFields =
                            listOf(
                                fieldWithPath("trend").type(JsonFieldType.NUMBER).description("트렌드 스크랩 수"),
                                fieldWithPath("grant").type(JsonFieldType.NUMBER).description("지원사업 스크랩 수"),
                            ),
                    ),
                )
            }
    }

    private fun toggle(
        token: String,
        targetType: String,
        targetId: Long,
    ) {
        mockMvc
            .post("/insights/scraps/toggle") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("targetType" to targetType, "targetId" to targetId))
            }.andExpect { status { isOk() } }
    }
}
