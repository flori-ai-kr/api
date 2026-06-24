package kr.ai.flori.verification.listener

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.notification.solapi.SolapiNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.verification.event.BusinessVerificationSubmittedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 사업자 인증 신청 → ① 운영자 Discord 알림 ② 점주에게 접수 알림톡(SOLAPI).
 * DB 커밋 후(AFTER_COMMIT) 비동기 발송(각 Notifier @Async).
 */
@Component
class BusinessVerificationEventListener(
    private val discordNotifier: DiscordNotifier,
    private val solapiNotifier: SolapiNotifier,
    private val userProfileRepository: UserProfileRepository,
) {
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

        val phone = userProfileRepository.findById(event.userId).map { it.phoneNumber }.orElse("")
        solapiNotifier.sendBusinessSubmitted(event.userId, phone, event.businessName)
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
