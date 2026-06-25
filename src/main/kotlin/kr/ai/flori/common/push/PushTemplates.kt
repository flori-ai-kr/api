package kr.ai.flori.common.push

import java.text.NumberFormat
import java.time.LocalTime
import java.util.Locale

data class PushContent(
    val title: String,
    val body: String,
    val url: String? = null,
)

data class DailySummaryItem(
    val time: LocalTime?,
    val customerName: String,
    val title: String,
)

object PushTemplates {
    private const val MAX_DAILY_LINES = 3
    private val krNumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    fun pickupReminder(
        time: LocalTime?,
        customerName: String,
        title: String,
        amount: Int,
    ): PushContent {
        val parts = mutableListOf<String>()
        if (time != null) parts.add(formatTime(time))
        parts.add("${customerName}님")
        parts.add(title)
        if (amount > 0) parts.add("${krNumberFormat.format(amount)}원")
        return PushContent(
            title = "예약 리마인더",
            body = parts.joinToString(" · "),
            url = "/calendar",
        )
    }

    fun dailySummary(items: List<DailySummaryItem>): PushContent {
        val sorted = items.sortedBy { it.time }
        val lines =
            sorted.take(MAX_DAILY_LINES).map { item ->
                val prefix = if (item.time != null) "${formatTime(item.time)} " else ""
                "$prefix${item.customerName}님 · ${item.title}"
            }
        val body =
            if (items.size > MAX_DAILY_LINES) {
                lines + "외 ${items.size - MAX_DAILY_LINES}건"
            } else {
                lines
            }
        return PushContent(
            title = "오늘 예약 ${items.size}건",
            body = body.joinToString("\n"),
            url = "/calendar",
        )
    }

    fun testNotification(): PushContent =
        PushContent(
            title = "테스트 알림",
            body = "푸시 알림이 정상적으로 동작합니다",
            url = "/",
        )

    @Suppress("MagicNumber")
    fun samplePickupReminder(): PushContent =
        pickupReminder(
            time = LocalTime.of(14, 0),
            customerName = "박지현",
            title = "졸업식 꽃다발",
            amount = 180000,
        )

    @Suppress("MagicNumber")
    fun sampleDailySummary(): PushContent =
        dailySummary(
            listOf(
                DailySummaryItem(LocalTime.of(9, 30), "홍길동", "프로포즈 부케"),
                DailySummaryItem(LocalTime.of(11, 0), "김영희", "개업 화환"),
                DailySummaryItem(LocalTime.of(14, 0), "이철수", "졸업식 꽃다발"),
                DailySummaryItem(LocalTime.of(16, 30), "박민수", "결혼식 장식"),
            ),
        )

    fun forTestType(type: String): PushContent =
        when (type) {
            "pickup_reminder" -> samplePickupReminder()
            "daily_summary" -> sampleDailySummary()
            else -> testNotification()
        }

    val testTypes: List<String> = listOf("pickup_reminder", "daily_summary", "test")

    private fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
}
