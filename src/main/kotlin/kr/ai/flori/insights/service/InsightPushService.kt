package kr.ai.flori.insights.service

import kr.ai.flori.common.job.JobNames
import kr.ai.flori.common.job.JobOutcome
import kr.ai.flori.common.job.JobRunRecorder
import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushTemplates
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.common.util.KST
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 인사이트(경매·지원사업) 스크랩/신규 기반 푸시. 적재 cron 직후 시간대에 실행.
 * 멱등(HARD): notification_log 원자적 claim으로 재기동/중복 트리거 시 1회만 발송. 건별 try-catch 격리.
 * 작업 로그(JobRunRecorder)로 감싸 콘솔에서 실행 현황·수동 트리거 가능.
 */
@Service
class InsightPushService(
    private val jdbcTemplate: JdbcTemplate,
    private val pushDispatcher: PushDispatcher,
    private val jobRunRecorder: JobRunRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${flori.auction-scrap-push.cron:0 35 6 * * *}", zone = "Asia/Seoul")
    fun scheduledAuctionScrapPush() = jobRunRecorder.record(JobNames.AUCTION_SCRAP_PUSH) { runAuctionScrapPush() }

    @Scheduled(cron = "\${flori.grant-new-push.cron:0 40 6 * * *}", zone = "Asia/Seoul")
    fun scheduledGrantNewPush() = jobRunRecorder.record(JobNames.GRANT_NEW_PUSH) { runGrantNewPush() }

    @Scheduled(cron = "\${flori.grant-deadline-push.cron:0 45 6 * * *}", zone = "Asia/Seoul")
    fun scheduledGrantDeadlinePush() = jobRunRecorder.record(JobNames.GRANT_DEADLINE_PUSH) { runGrantDeadlinePush() }

    /** 오늘 적재된 경매 품목을 스크랩한 유저별로 묶어 시세 업데이트 푸시. */
    fun runAuctionScrapPush(): JobOutcome {
        val today = LocalDate.now(KST)
        val since = Timestamp.from(today.atStartOfDay(KST).toInstant())
        val rows =
            jdbcTemplate.queryForList(
                "SELECT s.user_id AS uid, s.pum_name AS pum FROM flower_item_scraps s " +
                    "WHERE s.pum_name IN (SELECT DISTINCT pum_name FROM flower_auction_prices WHERE created_at >= ?)",
                since,
            )
        val byUser = rows.groupBy({ (it["uid"] as Number).toLong() }, { it["pum"] as String })
        var sent = 0
        byUser.forEach { (userId, pumNames) ->
            try {
                if (claimOnce(userId, PushTypes.AUCTION_SCRAP_UPDATE, today.toString())) {
                    val content = PushTemplates.auctionScrapUpdate(pumNames)
                    pushDispatcher.sendToUser(userId, content.title, content.body, content.link, PushTypes.AUCTION_SCRAP_UPDATE)
                    sent++
                }
            } catch (e: DataAccessException) {
                log.error("경매 스크랩 푸시 실패 userId={}", userId, e)
            }
        }
        return JobOutcome.success(sent)
    }

    /** 오늘 신규 추가된 지원사업이 있으면 전체 활성 유저에게 N건 알림. */
    fun runGrantNewPush(): JobOutcome {
        val today = LocalDate.now(KST)
        val since = Timestamp.from(today.atStartOfDay(KST).toInstant())
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_programs WHERE created_at >= ?", Long::class.java, since) ?: 0
        if (count == 0L) return JobOutcome.success(0)
        val firstTitle =
            jdbcTemplate
                .queryForList(
                    "SELECT title FROM support_programs WHERE created_at >= ? ORDER BY created_at DESC LIMIT 1",
                    String::class.java,
                    since,
                ).firstOrNull()
        val content = PushTemplates.grantNew(count.toInt(), firstTitle)
        var sent = 0
        jdbcTemplate.queryForList("SELECT id FROM users WHERE is_active", Long::class.java).forEach { userId ->
            try {
                if (claimOnce(userId, PushTypes.GRANT_NEW, today.toString())) {
                    pushDispatcher.sendToUser(userId, content.title, content.body, content.link, PushTypes.GRANT_NEW)
                    sent++
                }
            } catch (e: DataAccessException) {
                log.error("지원사업 신규 푸시 실패 userId={}", userId, e)
            }
        }
        return JobOutcome.success(sent, mapOf("newGrants" to count))
    }

    /** 스크랩한 지원사업 중 오늘(D-day)·내일(D-1) 마감 건을 해당 스크랩 유저에게 알림. */
    fun runGrantDeadlinePush(): JobOutcome {
        val today = LocalDate.now(KST)
        val programs =
            jdbcTemplate.queryForList(
                "SELECT id, title, apply_end FROM support_programs WHERE apply_end IN (?, ?)",
                Date.valueOf(today),
                Date.valueOf(today.plusDays(1)),
            )
        val sent = programs.sumOf { notifyDeadlineScrapers(it, today) }
        return JobOutcome.success(sent)
    }

    /** 한 공고의 스크랩 유저들에게 마감 알림. 발송한 건수 반환. */
    private fun notifyDeadlineScrapers(
        program: Map<String, Any?>,
        today: LocalDate,
    ): Int {
        val programId = (program["id"] as Number).toLong()
        val title = program["title"] as String
        val daysLeft = ChronoUnit.DAYS.between(today, (program["apply_end"] as Date).toLocalDate())
        val scraperIds =
            jdbcTemplate.queryForList(
                "SELECT user_id FROM insight_scraps WHERE target_type = 'grant' AND target_id = ?",
                Long::class.java,
                programId,
            )
        var sent = 0
        scraperIds.forEach { userId ->
            try {
                if (claimOnce(userId, PushTypes.GRANT_DEADLINE, "$programId-$today")) {
                    val content = PushTemplates.grantDeadline(title, daysLeft)
                    pushDispatcher.sendToUser(userId, content.title, content.body, content.link, PushTypes.GRANT_DEADLINE)
                    sent++
                }
            } catch (e: DataAccessException) {
                log.error("지원사업 마감 푸시 실패 userId={} programId={}", userId, programId, e)
            }
        }
        return sent
    }

    /** 알림 멱등 claim: 처음이면 true(발송), 이미 발송됐으면 false. */
    private fun claimOnce(
        userId: Long,
        type: String,
        dedupKey: String,
    ): Boolean =
        jdbcTemplate.update(
            "INSERT INTO notification_log (user_id, notification_type, dedup_key) VALUES (?::bigint, ?, ?) " +
                "ON CONFLICT (user_id, notification_type, dedup_key) DO NOTHING",
            userId,
            type,
            dedupKey,
        ) == 1
}
