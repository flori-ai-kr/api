package kr.ai.flori.admin.listener

import kr.ai.flori.admin.event.BusinessVerificationReviewedEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.notification.solapi.SolapiNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.user.repository.UserProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 인증 심사 결과 → ① 운영자 Discord 알림 ② 승인 시 점주에게 알림톡(SOLAPI) 통보.
 * DB 커밋 후(AFTER_COMMIT) 비동기 발송(각 Notifier @Async).
 * 신청 알림(BusinessVerificationEventListener)과 동일 Discord 채널을 사용한다.
 */
@Component
class BusinessVerificationReviewedListener(
    private val discordNotifier: DiscordNotifier,
    private val solapiNotifier: SolapiNotifier,
    private val userProfileRepository: UserProfileRepository,
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

        // 승인 시에만 점주에게 알림톡 통보(거절은 Task 4에서 추가). 전화번호는 프로필에서 조회.
        if (event.approved) {
            val phone = userProfileRepository.findById(event.userId).map { it.phoneNumber }.orElse("")
            solapiNotifier.sendBusinessApproved(event.userId, phone, event.businessName)
        }
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
