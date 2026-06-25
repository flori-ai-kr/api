package kr.ai.flori.admin.service

import kr.ai.flori.common.job.JobNames
import kr.ai.flori.common.job.JobOutcome
import kr.ai.flori.common.util.KST
import kr.ai.flori.expenses.service.RecurringExpenseGenerator
import kr.ai.flori.insights.service.BizinfoIngestService
import kr.ai.flori.insights.service.FlowerAuctionIngestService
import kr.ai.flori.insights.service.InsightPushService
import kr.ai.flori.insights.service.SupportProgramIngestService
import kr.ai.flori.reservations.service.ReservationNotificationService
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

/**
 * 수동 트리거용 작업 본문 레지스트리. job_name → 순수 본문(runXxx) 매핑.
 * 스케줄 경로와 동일한 본문을 호출하되 record() 래핑은 호출측(AdminJobRunService)이 담당한다(이중 기록 방지).
 */
@Component
class JobRegistry(
    flowerAuction: FlowerAuctionIngestService,
    supportProgram: SupportProgramIngestService,
    bizinfo: BizinfoIngestService,
    reservationNotification: ReservationNotificationService,
    recurringExpense: RecurringExpenseGenerator,
    insightPush: InsightPushService,
) {
    private val jobs: Map<String, () -> JobOutcome> =
        mapOf(
            JobNames.FLOWER_AUCTION_INGEST to flowerAuction::runIngest,
            JobNames.SUPPORT_PROGRAM_INGEST to supportProgram::runIngest,
            JobNames.BIZINFO_INGEST to bizinfo::runIngest,
            JobNames.RESERVATION_REMINDER to { reservationNotification.runReminderCheck(Instant.now()) },
            JobNames.DAILY_PICKUP_SUMMARY to { reservationNotification.runDailySummary(LocalDate.now(KST)) },
            JobNames.RECURRING_EXPENSE_GENERATE to { recurringExpense.runGenerate(LocalDate.now(KST)) },
            JobNames.AUCTION_SCRAP_PUSH to insightPush::runAuctionScrapPush,
            JobNames.GRANT_NEW_PUSH to insightPush::runGrantNewPush,
            JobNames.GRANT_DEADLINE_PUSH to insightPush::runGrantDeadlinePush,
        )

    fun has(jobName: String): Boolean = jobName in jobs

    fun body(jobName: String): (() -> JobOutcome)? = jobs[jobName]
}
