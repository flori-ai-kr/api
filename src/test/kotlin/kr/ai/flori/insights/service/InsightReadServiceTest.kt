package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.insights.dto.InstagramPostIngest
import kr.ai.flori.insights.dto.TrendArticleIngest
import kr.ai.flori.insights.repository.InstagramAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class InsightReadServiceTest {
    @Autowired
    lateinit var insightService: InsightService

    @Autowired
    lateinit var ingestService: InsightIngestService

    @Autowired
    lateinit var accountRepository: InstagramAccountRepository

    @Test
    fun `시드된 인스타 계정을 공유 읽기로 조회한다`() {
        // V2 시드 15개 이상
        assertThat(insightService.accounts(true).size).isGreaterThanOrEqualTo(15)
    }

    @Test
    fun `수집된 트렌드를 카테고리로 조회한다`() {
        val url = "https://ex.com/${UUID.randomUUID()}"
        ingestService.ingestTrends(listOf(TrendArticleIngest("flower", "꽃 트렌드", "요약", sourceUrl = url)))
        assertThat(insightService.trends("flower", 50, 0).map { it.sourceUrl }).contains(url)
    }

    @Test
    fun `수집된 포스트는 계정과 함께 반환된다`() {
        val accountId = requireNotNull(accountRepository.findByActiveTrueOrderBySortOrderAscUsernameAsc().first().id)
        val shortcode = "RD-${UUID.randomUUID()}"
        ingestService.ingestPosts(
            listOf(
                InstagramPostIngest(
                    accountId = accountId,
                    shortcode = shortcode,
                    permalink = "https://instagram.com/p/$shortcode",
                    postedAt = Instant.now(),
                ),
            ),
        )
        val posts = insightService.posts(accountId, null, "latest", null, 50)
        assertThat(posts).isNotEmpty()
        assertThat(posts.first().account).isNotNull()
    }
}
