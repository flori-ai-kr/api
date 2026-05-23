package com.hazel.reservations.service

import com.hazel.common.push.PushMessage
import com.hazel.common.push.PushService
import com.hazel.common.util.KST
import com.hazel.reservations.repository.ReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 예약 푸시 스케줄러(Vercel Cron 대체).
 * - 5분마다 도달한 리마인더 발송 + reminder_sent 마킹(중복 발송 방지).
 * - 매일 KST 08:00 사용자별 당일 픽업 요약 발송.
 * 토큰은 push_subscriptions에서 조회하고, 영구 실패 토큰은 비활성화한다.
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
        log.info("일일 픽업 요약 발송 대상 사용자: {}", count)
    }

    @Transactional
    fun markAndNotifyDueReminders(now: Instant): Int {
        val due = reservationRepository.findDueReminders(now)
        due.forEach { reservation ->
            notify(reservation.userId, "픽업 리마인더", "${reservation.title} · ${reservation.customerName}")
            reservation.reminderSent = true
            reservationRepository.save(reservation)
        }
        return due.size
    }

    // readOnly 아님: notify()가 영구실패 토큰 비활성화(UPDATE)를 수행할 수 있음
    @Transactional
    fun sendDailySummary(today: LocalDate): Int {
        val byUser = reservationRepository.findByDateAndStatusNot(today, "cancelled").groupBy { it.userId }
        byUser.forEach { (userId, reservations) ->
            notify(userId, "오늘의 픽업 ${reservations.size}건", reservations.joinToString(", ") { it.title })
        }
        return byUser.size
    }

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
}
