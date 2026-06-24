package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.KST
import kr.ai.flori.insights.domain.GrantCategories
import kr.ai.flori.insights.domain.ScrapTargetTypes
import kr.ai.flori.insights.dto.FlowerItemScrapToggleRequest
import kr.ai.flori.insights.dto.ScrapMemoRequest
import kr.ai.flori.insights.dto.ScrapToggleRequest
import kr.ai.flori.insights.entity.FlowerAuctionPrice
import kr.ai.flori.insights.entity.SupportProgram
import kr.ai.flori.insights.error.InsightErrorCode
import kr.ai.flori.insights.repository.FlowerAuctionPriceRepository
import kr.ai.flori.insights.repository.InsightScrapRepository
import kr.ai.flori.insights.repository.SupportProgramRepository
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class InsightServiceIntegrationTest {
    @Autowired
    lateinit var insightService: InsightService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var priceRepository: FlowerAuctionPriceRepository

    @Autowired
    lateinit var programRepository: SupportProgramRepository

    @Autowired
    lateinit var scrapRepository: InsightScrapRepository

    // 공유 테이블 + 개인 스크랩 모두 테스트 간 누적 방지를 위해 비운다.
    @AfterEach
    fun tearDown() {
        TenantContext.clear()
        scrapRepository.deleteAll()
        priceRepository.deleteAll()
        programRepository.deleteAll()
    }

    private fun newTenant(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun program(
        category: String? = GrantCategories.FUND,
        title: String = "지원사업",
        applyEnd: LocalDate? = null,
    ): SupportProgram =
        SupportProgram(source = "sbiz", sourceId = UUID.randomUUID().toString(), title = title).apply {
            this.category = category
            this.applyEnd = applyEnd
            this.agency = "소진공"
            this.summary = "요약"
        }

    // ── 경매 시세 ─────────────────────────────────────────────────────────

    @Test
    fun `화훼 카테고리 목록은 4종(절화 관엽 난 춘란) 고정이다`() {
        val categories = insightService.listFlowerCategories()
        assertThat(categories.map { it.code }).containsExactly("1", "2", "3", "4")
        assertThat(categories.map { it.label }).containsExactly("절화", "관엽", "난", "춘란")
    }

    @Test
    fun `경매 시세는 date 미지정 시 최신 정산일자를 쓰고 등락률과 출처를 채운다`() {
        priceRepository.save(priceRow(LocalDate.of(2026, 6, 16), 1000))
        priceRepository.save(priceRow(LocalDate.of(2026, 6, 17), 1100))

        val res = insightService.auctionPrices(null, null, null)
        assertThat(res.date).isEqualTo(LocalDate.of(2026, 6, 17))
        assertThat(res.source).isEqualTo("화훼유통정보(aT)")
        assertThat(res.prices).hasSize(1)
        assertThat(res.prices.first().flowerGubn).isEqualTo("절화")
        assertThat(res.prices.first().avgAmt).isEqualTo(1100)
        assertThat(res.prices.first().changeRate).isNotNull()
    }

    @Test
    fun `경매 시세 데이터가 없으면 date 는 null, prices 는 빈 목록(출처는 채움)`() {
        val res = insightService.auctionPrices(null, null, null)
        assertThat(res.date).isNull()
        assertThat(res.source).isEqualTo("화훼유통정보(aT)")
        assertThat(res.prices).isEmpty()
    }

    @Test
    fun `경매 정산일 목록은 distinct 최신순을 반환한다`() {
        priceRepository.save(priceRow(LocalDate.of(2026, 6, 16), 1000))
        priceRepository.save(priceRow(LocalDate.of(2026, 6, 17), 1100))
        priceRepository.save(priceRow(LocalDate.of(2026, 6, 18), 1200))

        assertThat(insightService.auctionDates(null))
            .containsExactly(LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 17), LocalDate.of(2026, 6, 16))
    }

    @Test
    fun `경매 요약은 날짜 미지정 시 데이터가 있는 최신 정산일을 쓴다(부분일이라도)`() {
        // 6/17: 10행, 6/18: 1행(부분) → 기본 기준일은 최신일 6/18(부분 여부 무관).
        repeat(10) { i ->
            priceRepository.save(summaryRow(LocalDate.of(2026, 6, 17), pum = "품목$i", avg = 1000, qty = 100, amt = 100_000))
        }
        priceRepository.save(summaryRow(LocalDate.of(2026, 6, 18), pum = "장미", avg = 9999, qty = 1, amt = 9999))

        val res = insightService.auctionSummary(null, null)
        assertThat(res.date).isEqualTo(LocalDate.of(2026, 6, 18))
        assertThat(res.source).isEqualTo("화훼유통정보(aT)")
        assertThat(res.items).hasSize(1)
    }

    @Test
    fun `경매 요약 등락률은 매칭 품종 등급 등락률의 중앙값(방식 A)이다`() {
        // 장미 변형 2개: 6/16 → 6/17, +10% 와 +30% → 중앙값 +20%.
        priceRepository.save(summaryRow(LocalDate.of(2026, 6, 16), pum = "장미", good = "레드", lv = "특2", avg = 1000, qty = 50, amt = 50_000))
        priceRepository.save(summaryRow(LocalDate.of(2026, 6, 16), pum = "장미", good = "옐로", lv = "상1", avg = 1000, qty = 50, amt = 50_000))
        priceRepository.save(
            summaryRow(LocalDate.of(2026, 6, 17), pum = "장미", good = "레드", lv = "특2", avg = 1100, qty = 100, amt = 110_000),
        )
        priceRepository.save(
            summaryRow(LocalDate.of(2026, 6, 17), pum = "장미", good = "옐로", lv = "상1", avg = 1300, qty = 300, amt = 390_000),
        )

        val res = insightService.auctionSummary(LocalDate.of(2026, 6, 17), null)
        assertThat(res.items).hasSize(1)
        val rose = res.items.first()
        assertThat(rose.pumName).isEqualTo("장미")
        assertThat(rose.variantCount).isEqualTo(2)
        // 가중평균 = (110000+390000)/(100+300) = 500000/400 = 1250.
        assertThat(rose.repAvg).isEqualTo(1250)
        assertThat(rose.repChangeRate!!).isCloseTo(
            0.20,
            org.assertj.core.api.Assertions
                .within(1e-9),
        )
    }

    @Test
    fun `경매 시세 드릴다운은 item 으로 품목을 필터한다`() {
        priceRepository.save(summaryRow(LocalDate.of(2026, 6, 17), pum = "장미", good = "레드", lv = "특2", avg = 1000, qty = 10, amt = 10_000))
        priceRepository.save(summaryRow(LocalDate.of(2026, 6, 17), pum = "튤립", good = "옐로", lv = "상1", avg = 2000, qty = 10, amt = 20_000))

        val res = insightService.auctionPrices(LocalDate.of(2026, 6, 17), null, "장미")
        assertThat(res.prices).hasSize(1)
        assertThat(res.prices.first().pumName).isEqualTo("장미")
    }

    @Test
    fun `경매 요약 데이터가 없으면 date 는 null, items 는 빈 목록(출처는 채움)`() {
        val res = insightService.auctionSummary(null, null)
        assertThat(res.date).isNull()
        assertThat(res.source).isEqualTo("화훼유통정보(aT)")
        assertThat(res.items).isEmpty()
    }

    private fun priceRow(
        date: LocalDate,
        avg: Int,
    ): FlowerAuctionPrice =
        FlowerAuctionPrice(saleDate = date, flowerGubn = "절화", pumName = "장미").apply {
            goodName = "레드"
            lvNm = "특2"
            avgAmt = avg
        }

    private fun summaryRow(
        date: LocalDate,
        pum: String,
        good: String = "레드",
        lv: String = "특2",
        avg: Int,
        qty: Long,
        amt: Long,
    ): FlowerAuctionPrice =
        FlowerAuctionPrice(saleDate = date, flowerGubn = "절화", pumName = pum).apply {
            goodName = good
            lvNm = lv
            avgAmt = avg
            totQty = qty
            totAmt = amt
        }

    // ── 지원사업 ──────────────────────────────────────────────────────────

    @Test
    fun `지원사업은 마감 임박순(nulls last)과 dDay 를 계산하고 만료 공고는 제외한다`() {
        val today = LocalDate.now(KST)
        programRepository.save(program(title = "마감없음", applyEnd = null))
        programRepository.save(program(title = "10일후", applyEnd = today.plusDays(10)))
        programRepository.save(program(title = "3일후", applyEnd = today.plusDays(3)))
        programRepository.save(program(title = "이미마감", applyEnd = today.minusDays(1))) // 만료 → 제외돼야 함

        val grants = insightService.listGrants(null, null, 0, 50)
        assertThat(grants.map { it.title }).containsExactly("3일후", "10일후", "마감없음") // "이미마감" 제외
        assertThat(grants[0].dDay).isEqualTo(3)
        assertThat(grants[1].dDay).isEqualTo(10)
        assertThat(grants[2].dDay).isNull()
    }

    @Test
    fun `지원사업 검색은 제목·기관을 대소문자 무시 부분일치로 거른다`() {
        val today = LocalDate.now(KST)
        programRepository.save(program(title = "청년 창업 자금 지원", applyEnd = today.plusDays(5)))
        programRepository.save(program(title = "수출 판로 마케팅 지원", applyEnd = today.plusDays(5)))

        // 제목 부분일치
        assertThat(insightService.listGrants(null, "마케팅", 0, 50).map { it.title })
            .containsExactly("수출 판로 마케팅 지원")
        // 기관명(헬퍼가 모두 "소진공")으로도 매칭
        assertThat(insightService.listGrants(null, "소진공", 0, 50)).hasSize(2)
        // 공백 키워드는 필터 없음(전체)
        assertThat(insightService.listGrants(null, "   ", 0, 50)).hasSize(2)
    }

    // ── 스크랩(개인) ──────────────────────────────────────────────────────

    @Test
    fun `스크랩 토글은 멱등이며 대상 존재를 검증한다`() {
        newTenant()
        val grant = programRepository.save(program())

        val first = insightService.toggleScrap(ScrapToggleRequest(ScrapTargetTypes.GRANT, grant.id))
        assertThat(first.scraped).isTrue()
        val second = insightService.toggleScrap(ScrapToggleRequest(ScrapTargetTypes.GRANT, grant.id))
        assertThat(second.scraped).isFalse()

        // 없는 대상은 404.
        assertThatThrownBy { insightService.toggleScrap(ScrapToggleRequest(ScrapTargetTypes.GRANT, 999_999L)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(InsightErrorCode.SCRAP_TARGET_NOT_FOUND)
            }
        // 잘못된 대상 유형은 400.
        assertThatThrownBy { insightService.toggleScrap(ScrapToggleRequest("post", grant.id)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(InsightErrorCode.INVALID_TARGET_TYPE)
            }
    }

    @Test
    fun `스크랩 메모는 upsert 되고 맵과 목록에 반영된다`() {
        newTenant()
        val grant = programRepository.save(program(title = "지원사업메모"))

        // 메모만으로 upsert 생성.
        val saved = insightService.updateScrapMemo(ScrapMemoRequest(ScrapTargetTypes.GRANT, grant.id, "기억할 것"))
        assertThat(saved.memo).isEqualTo("기억할 것")

        val map = insightService.scrapMap(ScrapTargetTypes.GRANT)
        assertThat(map).containsKey(grant.id.toString())
        assertThat(map[grant.id.toString()]?.memo).isEqualTo("기억할 것")

        val list = insightService.grantScraps(100)
        assertThat(list).hasSize(1)
        assertThat(list.first().program.title).isEqualTo("지원사업메모")
        assertThat(list.first().scrap.memo).isEqualTo("기억할 것")

        val counts = insightService.scrapCounts()
        assertThat(counts.grant).isEqualTo(1)
    }

    @Test
    fun `지원사업 스크랩 목록은 대상 사업과 조인된다`() {
        newTenant()
        val grant = programRepository.save(program(title = "교육지원", applyEnd = LocalDate.now(KST).plusDays(5)))
        insightService.toggleScrap(ScrapToggleRequest(ScrapTargetTypes.GRANT, grant.id))

        val list = insightService.grantScraps(100)
        assertThat(list).hasSize(1)
        assertThat(list.first().program.title).isEqualTo("교육지원")
        assertThat(list.first().program.dDay).isEqualTo(5)
    }

    @Test
    fun `스크랩은 테넌트별로 격리된다`() {
        val tenantA = newTenant()
        val grant = programRepository.save(program())
        insightService.toggleScrap(ScrapToggleRequest(ScrapTargetTypes.GRANT, grant.id))
        assertThat(insightService.scrapCounts().grant).isEqualTo(1)

        // 다른 테넌트에서는 보이지 않는다.
        val tenantB = newTenant()
        assertThat(tenantB).isNotEqualTo(tenantA)
        assertThat(insightService.scrapCounts().grant).isEqualTo(0)
        assertThat(insightService.scrapMap(ScrapTargetTypes.GRANT)).isEmpty()
        assertThat(insightService.grantScraps(100)).isEmpty()
    }

    @Test
    fun `경매 품목 스크랩은 멱등 토글되고 목록은 최신순으로 격리된다`() {
        newTenant()
        assertThat(insightService.toggleFlowerItemScrap(FlowerItemScrapToggleRequest("장미")).scraped).isTrue()
        assertThat(insightService.toggleFlowerItemScrap(FlowerItemScrapToggleRequest("국화")).scraped).isTrue()
        assertThat(insightService.flowerItemScrapNames().pumNames).containsExactlyInAnyOrder("장미", "국화")

        // 같은 품목 재토글 → 해제
        assertThat(insightService.toggleFlowerItemScrap(FlowerItemScrapToggleRequest("장미")).scraped).isFalse()
        assertThat(insightService.flowerItemScrapNames().pumNames).containsExactly("국화")

        // 다른 테넌트는 격리
        newTenant()
        assertThat(insightService.flowerItemScrapNames().pumNames).isEmpty()
    }
}
