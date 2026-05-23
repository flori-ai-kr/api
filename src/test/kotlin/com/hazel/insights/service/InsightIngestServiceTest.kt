package com.hazel.insights.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.insights.dto.InstagramAccountCreateRequest
import com.hazel.insights.dto.InstagramPostIngest
import com.hazel.insights.dto.TrendArticleIngest
import com.hazel.insights.repository.InstagramAccountRepository
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class InsightIngestServiceTest {
    @Autowired
    lateinit var ingestService: InsightIngestService

    @Autowired
    lateinit var accountRepository: InstagramAccountRepository

    private fun trend(url: String) =
        TrendArticleIngest(
            category = "flower",
            title = "장미 트렌드",
            summary = "요약",
            sourceUrl = url,
        )

    @Test
    fun `트렌드 수집은 멱등하다(중복 source_url 스킵)`() {
        val url = "https://example.com/${UUID.randomUUID()}"
        val first = ingestService.ingestTrends(listOf(trend(url)))
        assertThat(first.inserted).isEqualTo(1)

        val second = ingestService.ingestTrends(listOf(trend(url)))
        assertThat(second.inserted).isZero()
        assertThat(second.skipped).isEqualTo(1)
    }

    @Test
    fun `같은 배치 내 중복 source_url도 한 번만 적재`() {
        val url = "https://example.com/${UUID.randomUUID()}"
        val result = ingestService.ingestTrends(listOf(trend(url), trend(url)))
        assertThat(result.inserted).isEqualTo(1)
    }

    @Test
    fun `인스타 포스트 수집은 멱등하다(중복 shortcode 스킵)`() {
        val accountId = requireNotNull(accountRepository.findByActiveTrueOrderBySortOrderAscUsernameAsc().first().id)
        val shortcode = "SC-${UUID.randomUUID()}"
        val post =
            InstagramPostIngest(
                accountId = accountId,
                shortcode = shortcode,
                permalink = "https://instagram.com/p/$shortcode",
                postedAt = Instant.now(),
            )
        assertThat(ingestService.ingestPosts(listOf(post)).inserted).isEqualTo(1)
        assertThat(ingestService.ingestPosts(listOf(post)).inserted).isZero()
    }

    @Test
    fun `계정 등록과 중복 처리`() {
        val username = "florist_${UUID.randomUUID().toString().take(8)}"
        val created = ingestService.createAccount(InstagramAccountCreateRequest(username = username, region = "domestic"))
        assertThat(created.profileUrl).isEqualTo("https://www.instagram.com/$username")

        assertThatThrownBy { ingestService.createAccount(InstagramAccountCreateRequest(username = username, region = "domestic")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.DUPLICATE)
            }
    }
}
