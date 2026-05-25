package kr.ai.flori.reservations.service

import kr.ai.flori.common.push.PushMessage
import kr.ai.flori.common.push.PushService
import kr.ai.flori.common.util.KST
import kr.ai.flori.reservations.repository.ReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 예약 푸시 스케줄러(Vercel Cron 대체).
 * - 5분마다 도달한 리마인더 발송 + reminder_sent 마킹.
 * - 매일 KST 08:00 사용자별 당일 픽업 요약 발송.
 *
 * 멱등성(HARD): 리마인더는 reminder_sent 플래그, 일일 요약은 notification_log 원자적 claim
 *   (INSERT ... ON CONFLICT DO NOTHING)으로 재기동/중복 트리거 시에도 1회만 발송(at-most-once).
 * 실패 격리(HARD): 한 사용자/예약의 발송 실패가 다른 대상을 막지 않는다(건별 try-catch).
 *   PostgreSQL은 트랜잭션 내 한 문장 오류 시 전체가 abort되므로, 메서드 레벨 @Transactional을 두지 않고
 *   각 작업(save/claim/구독 비활성화)이 독립 커밋되게 해 건별로 격리한다.
 *   잡 레벨 격리(한 회차 실패가 다음 회차를 막지 않음)는 Spring 스케줄러가 메서드별로 제공한다.
 */
@Service
class ReservationNotificationService(
    private val reservationRepository: ReservationRepository,
    private val pushService: PushService,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 */5 * * * *")
    fun scheduledReminderCheck() {
        val count = markAndNotifyDueReminders(Instant.now())
        if (count > 0) log.info("리마인더 발송: {}건", count)
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    fun scheduledDailySummary() {
        val count = sendDailySummary(LocalDate.now(KST))
        log.info("일일 픽업 요약 발송 사용자: {}", count)
    }

    /**
     * 도달한 리마인더를 발송하고 reminder_sent로 마킹한다(중복 발송 방지).
     * 건별 격리: 한 예약 발송 실패가 나머지를 막지 않는다.
     */
    fun markAndNotifyDueReminders(now: Instant): Int {
        var sent = 0
        reservationRepository.findDueReminders(now).forEach { reservation ->
            try {
                notify(reservation.userId, "픽업 리마인더", "${reservation.title} · ${reservation.customerName}")
                reservation.reminderSent = true
                reservationRepository.save(reservation)
                sent++
            } catch (e: DataAccessException) {
                log.error("리마인더 발송 실패 reservationId={}", reservation.id, e)
            }
        }
        return sent
    }

    /**
     * 당일 비취소 예약이 있는 사용자에게 픽업 요약을 1회 발송한다.
     * notification_log 원자적 claim으로 재기동/중복 트리거 시에도 1회만 발송. 건별 격리.
     * @return 실제로 발송한(claim에 성공한) 사용자 수.
     */
    fun sendDailySummary(today: LocalDate): Int {
        val byUser = reservationRepository.findByDateAndStatusNot(today, "cancelled").groupBy { it.userId }
        var sent = 0
        byUser.forEach { (userId, reservations) ->
            try {
                if (claimOnce(userId, DAILY_SUMMARY, today.toString())) {
                    notify(userId, "오늘의 픽업 ${reservations.size}건", reservations.joinToString(", ") { it.title })
                    sent++
                }
            } catch (e: DataAccessException) {
                log.error("일일 요약 발송 실패 userId={}", userId, e)
            }
        }
        return sent
    }

    /** 알림 멱등성 claim: 처음이면 true(발송 진행), 이미 발송됐으면 false. */
    private fun claimOnce(
        userId: UUID,
        type: String,
        dedupKey: String,
    ): Boolean =
        jdbcTemplate.update(
            "INSERT INTO notification_log (user_id, notification_type, dedup_key) VALUES (?::uuid, ?, ?) " +
                "ON CONFLICT (user_id, notification_type, dedup_key) DO NOTHING",
            userId,
            type,
            dedupKey,
        ) == 1

    private fun notify(
        userId: UUID,
        title: String,
        body: String,
    ) {
        val tokens =
            jdbcTemplate.queryForList(
                "SELECT endpoint FROM push_subscriptions WHERE user_id = ?::uuid AND is_active = TRUE",
                String::class.java,
                userId,
            )
        tokens.forEach { token ->
            val result = pushService.send(PushMessage(token = token, title = title, body = body))
            if (result.tokenInvalid) {
                jdbcTemplate.update("UPDATE push_subscriptions SET is_active = FALSE WHERE endpoint = ?", token)
            }
        }
    }

    private companion object {
        const val DAILY_SUMMARY = "daily_summary"
    }
}
