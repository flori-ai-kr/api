package kr.ai.flori.common.push

/**
 * 사용자 푸시 타입 SSOT. notification_preferences.type 및 발송 시 수신설정 키와 1:1.
 * TOGGLEABLE = 점주가 끌 수 있는 타입(수신설정 대상). 그 외(테스트 등)는 항상 발송.
 */
object PushTypes {
    const val RESERVATION_REMINDER = "reservation_reminder"
    const val DAILY_PICKUP_SUMMARY = "daily_pickup_summary"
    const val BROADCAST = "broadcast"

    /** 점주 수신설정으로 끌 수 있는 타입(콘솔/설정 노출 순서). */
    val TOGGLEABLE = listOf(RESERVATION_REMINDER, DAILY_PICKUP_SUMMARY, BROADCAST)
}
