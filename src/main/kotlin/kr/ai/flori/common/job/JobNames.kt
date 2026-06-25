package kr.ai.flori.common.job

/**
 * 백그라운드 작업 식별자 SSOT. job_run_status/job_run_logs.job_name 및 수동 트리거 키와 1:1.
 * 프론트 라벨 매핑은 web 콘솔이 보유.
 */
object JobNames {
    const val FLOWER_AUCTION_INGEST = "flower_auction_ingest"
    const val SUPPORT_PROGRAM_INGEST = "support_program_ingest"
    const val BIZINFO_INGEST = "bizinfo_ingest"
    const val RESERVATION_REMINDER = "reservation_reminder"
    const val DAILY_PICKUP_SUMMARY = "daily_pickup_summary"
    const val RECURRING_EXPENSE_GENERATE = "recurring_expense_generate"

    /** 수동 트리거 허용 목록(콘솔 노출 순서). */
    val ALL =
        listOf(
            FLOWER_AUCTION_INGEST,
            SUPPORT_PROGRAM_INGEST,
            BIZINFO_INGEST,
            RESERVATION_REMINDER,
            DAILY_PICKUP_SUMMARY,
            RECURRING_EXPENSE_GENERATE,
        )
}
