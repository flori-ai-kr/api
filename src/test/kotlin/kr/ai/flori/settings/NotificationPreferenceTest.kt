package kr.ai.flori.settings

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.notification.NotificationSendRecorder
import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushMessage
import kr.ai.flori.common.push.PushResult
import kr.ai.flori.common.push.PushService
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.common.push.WebPushPayload
import kr.ai.flori.common.push.WebPushResult
import kr.ai.flori.common.push.WebPushSender
import kr.ai.flori.common.push.WebPushTarget
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.service.NotificationPreferenceService
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class NotificationPreferenceTest {
    @Autowired
    lateinit var preferenceService: NotificationPreferenceService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    // 발송 성공을 환경(Vapid/Logging 폴백)에 의존하지 않도록 항상 성공하는 stub sender로 구성한다.
    // 검증 대상은 수신설정에 따른 발송 여부(스킵)이지 실제 전송이 아니다.
    private val pushDispatcher by lazy {
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
            sendRecorder =
                object : NotificationSendRecorder {
                    override fun record(
                        source: String,
                        type: String,
                        success: Boolean,
                        targetUserId: Long?,
                        title: String?,
                        errorMessage: String?,
                    ) = Unit
                },
        )
    }

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newUser(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun addActiveSubscription(userId: Long) {
        jdbcTemplate.update(
            "INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, is_active) VALUES (?, ?, ?, ?, TRUE)",
            userId,
            "ep-$userId",
            "p256dh-key",
            "auth-key",
        )
    }

    // ── 수신설정 서비스 ──────────────────────────────────────────────────────

    @Test
    fun `설정 없으면 모든 토글 타입이 기본 켜짐`() {
        newUser()
        val prefs = preferenceService.list()

        assertThat(prefs.map { it.type }).containsExactlyElementsOf(PushTypes.TOGGLEABLE)
        assertThat(prefs).allMatch { it.enabled }
    }

    @Test
    fun `토글 끄면 해당 타입만 false`() {
        newUser()
        preferenceService.set(PushTypes.RESERVATION_REMINDER, false)

        val prefs = preferenceService.list().associate { it.type to it.enabled }
        assertThat(prefs[PushTypes.RESERVATION_REMINDER]).isFalse()
        assertThat(prefs[PushTypes.DAILY_PICKUP_SUMMARY]).isTrue()
        assertThat(prefs[PushTypes.BROADCAST]).isTrue()
    }

    @Test
    fun `토글 가능하지 않은 타입은 거부`() {
        newUser()
        assertThatThrownBy { preferenceService.set("unknown_type", false) }
            .isInstanceOf(AppException::class.java)
    }

    // ── 발송 시 수신설정 반영 (PushDispatcher) ───────────────────────────────

    @Test
    fun `기본(설정 없음)이면 발송된다`() {
        val userId = newUser()
        addActiveSubscription(userId)

        val sent = pushDispatcher.sendToUser(userId, "제목", "본문", null, PushTypes.RESERVATION_REMINDER)
        assertThat(sent).isEqualTo(1)
    }

    @Test
    fun `해당 타입을 끄면 발송하지 않는다`() {
        val userId = newUser()
        addActiveSubscription(userId)
        preferenceService.set(PushTypes.RESERVATION_REMINDER, false)

        val sent = pushDispatcher.sendToUser(userId, "제목", "본문", null, PushTypes.RESERVATION_REMINDER)
        assertThat(sent).isEqualTo(0)
    }

    @Test
    fun `다른 타입을 꺼도 영향 없다`() {
        val userId = newUser()
        addActiveSubscription(userId)
        preferenceService.set(PushTypes.BROADCAST, false)

        val sent = pushDispatcher.sendToUser(userId, "제목", "본문", null, PushTypes.RESERVATION_REMINDER)
        assertThat(sent).isEqualTo(1)
    }

    @Test
    fun `비-토글 타입(테스트 등)은 수신설정 무관하게 발송된다`() {
        val userId = newUser()
        addActiveSubscription(userId)
        // 무관한 토글 타입을 꺼둬도 비-토글 타입(TEST) 발송엔 영향 없다
        preferenceService.set(PushTypes.RESERVATION_REMINDER, false)

        val sent = pushDispatcher.sendToUser(userId, "제목", "본문", null, PushTypes.TEST)
        assertThat(sent).isEqualTo(1)
    }
}
