package kr.ai.flori.insights.repository

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.insights.entity.FlowerAuctionPrice
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * 경매 시세 파생(등락률·최신일자) 네이티브 SQL 직접 검증. 단일 시장(aT 양재).
 * 공유 테이블이라 테스트 간 누적 방지를 위해 @AfterEach 에서 비운다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class FlowerAuctionPriceQueryRepositoryTest {
    @Autowired
    lateinit var queryRepository: FlowerAuctionPriceQueryRepository

    @Autowired
    lateinit var priceRepository: FlowerAuctionPriceRepository

    @AfterEach
    fun tearDown() {
        priceRepository.deleteAll()
    }

    private fun price(
        date: LocalDate,
        flowerGubn: String,
        pumName: String,
        goodName: String,
        lvNm: String,
        avg: Int?,
    ): FlowerAuctionPrice =
        FlowerAuctionPrice(saleDate = date, flowerGubn = flowerGubn, pumName = pumName).apply {
            this.goodName = goodName
            this.lvNm = lvNm
            this.avgAmt = avg
            this.maxAmt = avg?.plus(1000)
            this.minAmt = avg?.minus(1000)
            this.totQty = avg?.toLong()?.times(3)
            this.totAmt = avg?.toLong()?.times(10)
        }

    @Test
    fun `latestDate 는 데이터가 있는 가장 최근 정산일자를 반환한다`() {
        priceRepository.save(price(LocalDate.of(2026, 6, 16), "절화", "장미", "레드", "특2", 1000))
        priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", 1200))

        assertThat(queryRepository.latestDate(null, null)).isEqualTo(LocalDate.of(2026, 6, 17))
    }

    @Test
    fun `latestDate 는 필터(gubn item)를 반영한다`() {
        priceRepository.save(price(LocalDate.of(2026, 6, 16), "절화", "장미", "레드", "특2", 1000))
        priceRepository.save(price(LocalDate.of(2026, 6, 18), "관엽", "튤립", "옐로", "상1", 1500))

        assertThat(queryRepository.latestDate("절화", null)).isEqualTo(LocalDate.of(2026, 6, 16))
        assertThat(queryRepository.latestDate(null, "튤립")).isEqualTo(LocalDate.of(2026, 6, 18))
    }

    @Test
    fun `ratesOn 은 직전 정산일자 대비 등락률을 파생 계산한다`() {
        // 같은 (gubn,품목,품종,등급) 시계열: 6/15=1000 → 6/17=1200 (+20%)
        priceRepository.save(price(LocalDate.of(2026, 6, 15), "절화", "장미", "레드", "특2", 1000))
        priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", 1200))

        val rows = queryRepository.ratesOn(LocalDate.of(2026, 6, 17), null, null)

        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.flowerGubn).isEqualTo("절화")
        assertThat(row.pumName).isEqualTo("장미")
        assertThat(row.avgAmt).isEqualTo(1200)
        assertThat(row.prevAvgAmt).isEqualTo(1000)
        assertThat(row.changeRate).isCloseTo(0.20, within(1e-9))
    }

    @Test
    fun `ratesOn 은 직전일이 없으면 changeRate 가 null 이다`() {
        priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", 1200))

        val rows = queryRepository.ratesOn(LocalDate.of(2026, 6, 17), null, null)

        assertThat(rows).hasSize(1)
        assertThat(rows.first().prevAvgAmt).isNull()
        assertThat(rows.first().changeRate).isNull()
    }

    @Test
    fun `ratesOn 은 대상일 행만 남기고 그 이전 행은 직전값 계산에만 쓴다`() {
        // 3일치 시계열: 대상일(6/17) 행 1건만 반환, prev 는 6/16 값.
        priceRepository.save(price(LocalDate.of(2026, 6, 15), "절화", "장미", "레드", "특2", 800))
        priceRepository.save(price(LocalDate.of(2026, 6, 16), "절화", "장미", "레드", "특2", 1000))
        priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", 900))

        val rows = queryRepository.ratesOn(LocalDate.of(2026, 6, 17), null, null)

        assertThat(rows).hasSize(1)
        assertThat(rows.first().prevAvgAmt).isEqualTo(1000)
        assertThat(rows.first().changeRate).isCloseTo(-0.10, within(1e-9))
    }

    @Test
    fun `ratesOn 은 gubn item 필터를 반영한다`() {
        priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", 1000))
        priceRepository.save(price(LocalDate.of(2026, 6, 17), "관엽", "튤립", "옐로", "상1", 2000))

        val onlyCut = queryRepository.ratesOn(LocalDate.of(2026, 6, 17), "절화", null)
        assertThat(onlyCut).hasSize(1)
        assertThat(onlyCut.first().pumName).isEqualTo("장미")

        val onlyTulip = queryRepository.ratesOn(LocalDate.of(2026, 6, 17), null, "튤립")
        assertThat(onlyTulip).hasSize(1)
        assertThat(onlyTulip.first().flowerGubn).isEqualTo("관엽")
    }

    // ── distinctDates ─────────────────────────────────────────────────────

    @Test
    fun `distinctDates 는 정산일자 distinct 최신순을 반환하고 cap 을 적용한다`() {
        priceRepository.save(price(LocalDate.of(2026, 6, 16), "절화", "장미", "레드", "특2", 1000))
        priceRepository.save(price(LocalDate.of(2026, 6, 16), "절화", "장미", "옐로", "상1", 1100)) // 같은 날 중복
        priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", 1200))
        priceRepository.save(price(LocalDate.of(2026, 6, 18), "관엽", "튤립", "옐로", "상1", 1500))

        assertThat(queryRepository.distinctDates(null, 60))
            .containsExactly(LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 17), LocalDate.of(2026, 6, 16))

        // gubn 필터 반영.
        assertThat(queryRepository.distinctDates("절화", 60))
            .containsExactly(LocalDate.of(2026, 6, 17), LocalDate.of(2026, 6, 16))

        // cap 적용(최신 1건).
        assertThat(queryRepository.distinctDates(null, 1)).containsExactly(LocalDate.of(2026, 6, 18))
    }

    // ── latestCompleteDate ────────────────────────────────────────────────

    @Test
    fun `latestCompleteDate 는 부분 적재된 최신일을 건너뛰고 완전한 날을 고른다`() {
        // 6/17: 10행(완전). 6/18: 1행(부분, 임계 0.5*10=5 미만) → 6/17 선택.
        repeat(10) { i ->
            priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "품목$i", "레드", "특2", 1000 + i))
        }
        priceRepository.save(price(LocalDate.of(2026, 6, 18), "절화", "장미", "레드", "특2", 9999))

        assertThat(queryRepository.latestCompleteDate(null)).isEqualTo(LocalDate.of(2026, 6, 17))
    }

    @Test
    fun `latestCompleteDate 는 최신일이 충분히 크면 그 날을 고른다`() {
        repeat(10) { i ->
            priceRepository.save(price(LocalDate.of(2026, 6, 17), "절화", "품목$i", "레드", "특2", 1000 + i))
        }
        // 6/18: 6행(임계 0.5*10=5 이상) → 최신일 채택.
        repeat(6) { i ->
            priceRepository.save(price(LocalDate.of(2026, 6, 18), "절화", "품목$i", "레드", "특2", 2000 + i))
        }

        assertThat(queryRepository.latestCompleteDate(null)).isEqualTo(LocalDate.of(2026, 6, 18))
    }

    @Test
    fun `latestCompleteDate 는 데이터가 없으면 null`() {
        assertThat(queryRepository.latestCompleteDate(null)).isNull()
    }

    // ── summaryOn (등락 방식 A = 매칭 품종·등급 중앙값) ──────────────────────

    @Test
    fun `summaryOn 은 거래량 가중평균과 매칭 변형 등락률 중앙값을 계산한다`() {
        // 같은 품목(장미) 3개 변형. 직전일(6/16) 평균가 → 대상일(6/17) 평균가.
        // 등락률: 변형1 +10%(1000→1100), 변형2 +20%(1000→1200), 변형3 직전 없음(null) → 매칭 2건 중앙값=15%.
        priceRepository.save(price(LocalDate.of(2026, 6, 16), "절화", "장미", "레드", "특2", 1000))
        priceRepository.save(price(LocalDate.of(2026, 6, 16), "절화", "장미", "옐로", "상1", 1000))
        priceRepository.save(rowWith(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", avg = 1100, qty = 100, amt = 110_000))
        priceRepository.save(rowWith(LocalDate.of(2026, 6, 17), "절화", "장미", "옐로", "상1", avg = 1200, qty = 300, amt = 360_000))
        priceRepository.save(rowWith(LocalDate.of(2026, 6, 17), "절화", "장미", "핑크", "보3", avg = 5000, qty = 0, amt = 0))

        val rows = queryRepository.summaryOn(LocalDate.of(2026, 6, 17), null)

        assertThat(rows).hasSize(1)
        val rose = rows.first()
        assertThat(rose.pumName).isEqualTo("장미")
        assertThat(rose.variantCount).isEqualTo(3)
        // 가중평균 = (110000+360000+0) / (100+300+0) = 470000/400 = 1175.
        assertThat(rose.repAvg).isEqualTo(1175)
        // 매칭 변형 2건(+10%, +20%)의 중앙값 = +15%.
        assertThat(rose.repChangeRate).isCloseTo(0.15, within(1e-9))
    }

    @Test
    fun `summaryOn 은 매칭 변형이 없으면 repChangeRate 가 null 이고 거래량 많은 순으로 정렬한다`() {
        // 거래량: 장미(400) > 튤립(100). 둘 다 직전일 없음 → repChangeRate null.
        priceRepository.save(rowWith(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", avg = 1000, qty = 400, amt = 400_000))
        priceRepository.save(rowWith(LocalDate.of(2026, 6, 17), "절화", "튤립", "옐로", "상1", avg = 2000, qty = 100, amt = 200_000))

        val rows = queryRepository.summaryOn(LocalDate.of(2026, 6, 17), null)

        assertThat(rows.map { it.pumName }).containsExactly("장미", "튤립")
        assertThat(rows.first().repChangeRate).isNull()
        assertThat(rows.first().repAvg).isEqualTo(1000)
    }

    @Test
    fun `summaryOn 은 gubn 필터를 반영한다`() {
        priceRepository.save(rowWith(LocalDate.of(2026, 6, 17), "절화", "장미", "레드", "특2", avg = 1000, qty = 10, amt = 10_000))
        priceRepository.save(rowWith(LocalDate.of(2026, 6, 17), "관엽", "튤립", "옐로", "상1", avg = 2000, qty = 10, amt = 20_000))

        val onlyCut = queryRepository.summaryOn(LocalDate.of(2026, 6, 17), "절화")
        assertThat(onlyCut.map { it.pumName }).containsExactly("장미")
    }

    private fun rowWith(
        date: LocalDate,
        flowerGubn: String,
        pumName: String,
        goodName: String,
        lvNm: String,
        avg: Int?,
        qty: Long,
        amt: Long,
    ): FlowerAuctionPrice =
        FlowerAuctionPrice(saleDate = date, flowerGubn = flowerGubn, pumName = pumName).apply {
            this.goodName = goodName
            this.lvNm = lvNm
            this.avgAmt = avg
            this.maxAmt = avg?.plus(1000)
            this.minAmt = avg?.minus(1000)
            this.totQty = qty
            this.totAmt = amt
        }
}
