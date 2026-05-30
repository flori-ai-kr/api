package kr.ai.flori.admin.listener

import kr.ai.flori.admin.event.BusinessVerificationReviewedEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 인증 심사 결과 → Discord 알림. DB 커밋 후(AFTER_COMMIT) 비동기 발송(DiscordNotifier @Async).
 * 신청 알림(BusinessVerificationEventListener)과 동일 채널을 사용한다.
 */
@Component
class BusinessVerificationReviewedListener(
    private val discordNotifier: DiscordNotifier,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: BusinessVerificationReviewedEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val verdict = if (event.approved) "승인 ✅" else "거절 ❌"
        val reasonLine = if (!event.approved) "\n- 사유: ${event.reason}" else ""
        val message =
            """
            **[사업자 인증 심사 $verdict]**
            - 일시: $now
            - userId: ${event.userId}
            - 상호: ${event.businessName}$reasonLine
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.VERIFICATION, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
