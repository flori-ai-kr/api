package kr.ai.flori.verification.listener

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.verification.event.BusinessVerificationSubmittedEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 사업자 인증 신청 → Discord 알림. DB 커밋 후(AFTER_COMMIT) 비동기 발송.
 * 관리자는 링크로 등록증을 확인한 뒤 수동으로 승인/거절(SQL)한다.
 */
@Component
class BusinessVerificationEventListener(
    private val discordNotifier: DiscordNotifier,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: BusinessVerificationSubmittedEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val message =
            """
            **[사업자 인증 신청 📄]**
            - 신청 일자: $now
            - userId: ${event.userId}
            - 상호: ${event.businessName}
            - 사업자번호: ${event.businessNumber}
            - 대표자명: ${event.representativeName}
            - 등록증: ${event.businessLicenseUrl}
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.VERIFICATION, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
