package kr.ai.flori.insights.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.job.JobRunRecorder
import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushMessage
import kr.ai.flori.common.push.PushResult
import kr.ai.flori.common.push.PushService
import kr.ai.flori.common.push.WebPushPayload
import kr.ai.flori.common.push.WebPushResult
import kr.ai.flori.common.push.WebPushSender
import kr.ai.flori.common.push.WebPushTarget
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Date
import java.time.LocalDate

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class InsightPushServiceTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var jobRunRecorder: JobRunRecorder

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    // 발송 성공을 환경에 의존하지 않도록 항상 성공하는 stub sender로 구성.
    private val service by lazy {
        InsightPushService(
            jdbcTemplate = jdbcTemplate,
            pushDispatcher =
                PushDispatcher(
                    fcm =
                        object : PushService {
                            override fun send(message: PushMessage) = PushResult(success = true)
                        },
                    webPush =
                        object : WebPushSender {
                            override fun send(
                                target: WebPushTarget,
                                payload: WebPushPayload,
                            ) = WebPushResult(success = true)
                        },
                    jdbcTemplate = jdbcTemplate,
                ),
            jobRunRecorder = jobRunRecorder,
        )
    }

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newUser(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun addSubscription(userId: Long) {
        jdbcTemplate.update(
            "INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, is_active) VALUES (?, ?, ?, ?, TRUE)",
            userId,
            "ep-$userId-${System.nanoTime()}",
            "p256dh-key",
            "auth-key",
        )
    }

    private fun insertGrant(applyEnd: LocalDate): Long {
        val sourceId = "src-${System.nanoTime()}"
        jdbcTemplate.update(
            "INSERT INTO support_programs (source, source_id, title, apply_end) VALUES ('k-startup', ?, ?, ?)",
            sourceId,
            "소상공인 온라인판로 지원사업",
            Date.valueOf(applyEnd),
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM support_programs WHERE source_id = ?",
            Long::class.java,
            sourceId,
        )!!
    }

    @Test
    fun `경매 스크랩 - 오늘 시세 들어온 스크랩 품목 유저에게 발송`() {
        val userId = newUser()
        addSubscription(userId)
        jdbcTemplate.update(
            "INSERT INTO flower_auction_prices (sale_date, flower_gubn, pum_name, good_name, lv_nm) VALUES (?, ?, ?, ?, ?)",
            Date.valueOf(LocalDate.now()),
            "fl",
            "국화",
            "혼합",
            "특",
        )
        jdbcTemplate.update("INSERT INTO flower_item_scraps (user_id, pum_name) VALUES (?, ?)", userId, "국화")

        val outcome = service.runAuctionScrapPush()

        assertThat(outcome.processedCount).isEqualTo(1)
    }

    @Test
    fun `지원사업 마감 - 내일 마감 스크랩 유저에게 발송`() {
        val userId = newUser()
        addSubscription(userId)
        val grantId = insertGrant(applyEnd = LocalDate.now().plusDays(1))
        jdbcTemplate.update("INSERT INTO insight_scraps (user_id, target_type, target_id) VALUES (?, 'grant', ?)", userId, grantId)

        val outcome = service.runGrantDeadlinePush()

        assertThat(outcome.processedCount).isEqualTo(1)
    }

    @Test
    fun `지원사업 신규 - 오늘 적재 건 있으면 발송 대상 존재`() {
        val userId = newUser()
        addSubscription(userId)
        insertGrant(applyEnd = LocalDate.now().plusDays(30)) // created_at = NOW() (오늘 신규)

        val outcome = service.runGrantNewPush()

        // 방금 만든 유저 포함 활성 유저에게 발송(claim 성공)
        assertThat(outcome.processedCount).isGreaterThanOrEqualTo(1)
    }
}
