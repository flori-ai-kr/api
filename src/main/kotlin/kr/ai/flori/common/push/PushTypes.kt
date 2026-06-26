package kr.ai.flori.common.push

/**
 * 사용자 푸시 타입 SSOT. notification_preferences.type 및 발송 시 수신설정 키와 1:1.
 * TOGGLEABLE = 점주가 끌 수 있는 타입(수신설정 대상). 그 외(테스트 등)는 항상 발송.
 */
object PushTypes {
    const val RESERVATION_REMINDER = "reservation_reminder"
    const val DAILY_PICKUP_SUMMARY = "daily_pickup_summary"
    const val BROADCAST = "broadcast"
    const val COMMUNITY_NOTICE = "community_notice"
    const val COMMUNITY_COMMENT = "community_comment"
    const val AUCTION_SCRAP_UPDATE = "auction_scrap_update"
    const val GRANT_NEW = "grant_new"
    const val GRANT_DEADLINE = "grant_deadline"
    const val STORAGE_RESOLVED = "storage_resolved"

    // 비토글(항상 발송) — 발송 로그 타입 라벨용.
    const val INQUIRY_ANSWERED = "inquiry_answered"
    const val TEST = "test"

    /**
     * 점주 수신설정으로 끌 수 있는 타입(콘솔/설정 노출 순서). community_notice(공지)는 강제 발송이라 제외 —
     * TOGGLEABLE이 아닌 타입은 PushDispatcher가 수신설정과 무관하게 항상 발송한다.
     */
    val TOGGLEABLE =
        listOf(
            RESERVATION_REMINDER,
            DAILY_PICKUP_SUMMARY,
            BROADCAST,
            COMMUNITY_COMMENT,
            AUCTION_SCRAP_UPDATE,
            GRANT_NEW,
            GRANT_DEADLINE,
        )
}
