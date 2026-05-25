package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.insights.entity.TrendArticle
import kr.ai.flori.insights.repository.TrendArticleRepository
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
class ScrapServiceIntegrationTest {
    @Autowired
    lateinit var scrapService: ScrapService

    @Autowired
    lateinit var trendRepository: TrendArticleRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): UUID {
        val email = "scrap-${UUID.randomUUID()}@flori.dev"
        authService.signup(SignupRequest(email, "password123", null))
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun newTrend(): UUID {
        val t = TrendArticle("flower", "트렌드", "요약", "https://ex.com/${UUID.randomUUID()}", LocalDate.now())
        return requireNotNull(trendRepository.save(t).id)
    }

    @Test
    fun `토글로 스크랩 추가·해제`() {
        newTenant()
        val trendId = newTrend()
        assertThat(scrapService.toggle("trend", trendId)).isTrue()
        assertThat(scrapService.counts().trend).isEqualTo(1)
        assertThat(scrapService.toggle("trend", trendId)).isFalse()
        assertThat(scrapService.counts().trend).isZero()
    }

    @Test
    fun `존재하지 않는 대상은 스크랩할 수 없다`() {
        newTenant()
        assertThatThrownBy { scrapService.toggle("trend", UUID.randomUUID()) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `메모는 스크랩 후에만 저장 가능`() {
        newTenant()
        val trendId = newTrend()
        assertThatThrownBy { scrapService.updateMemo("trend", trendId, "메모") }
            .isInstanceOf(AppException::class.java)
        scrapService.toggle("trend", trendId)
        assertThat(scrapService.updateMemo("trend", trendId, "좋은 트렌드").memo).isEqualTo("좋은 트렌드")
    }

    @Test
    fun `트렌드 스크랩 목록과 맵을 반환한다`() {
        newTenant()
        val trendId = newTrend()
        scrapService.toggle("trend", trendId)
        assertThat(scrapService.trendScraps(100)).hasSize(1)
        assertThat(scrapService.scrapMap("trend")).containsKey(trendId)
    }

    @Test
    fun `다른 테넌트의 스크랩은 보이지 않는다`() {
        newTenant()
        val trendId = newTrend()
        scrapService.toggle("trend", trendId)
        newTenant()
        assertThat(scrapService.counts().trend).isZero()
        assertThat(scrapService.scrapMap("trend")).isEmpty()
    }
}
