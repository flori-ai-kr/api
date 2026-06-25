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

@Suppress("TooManyFunctions") // 푸시 타입별 템플릿 SSOT — 타입 수만큼 함수가 늘어나는 게 의도.
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

    // ── 신규 5종 ──────────────────────────────────────────────────────────────

    /** 커뮤니티 공지글 작성 → 전체 발송. */
    fun communityNotice(
        postId: Long,
        title: String,
    ): PushContent =
        PushContent(
            title = "새 공지사항",
            body = title,
            url = "/community/posts/$postId",
        )

    /** 댓글/답글 알림. 비밀이면 본문을 마스킹한다. */
    fun communityComment(
        postId: Long,
        content: String,
        isReply: Boolean,
        isSecret: Boolean,
    ): PushContent =
        PushContent(
            title = if (isReply) "새 답글이 달렸습니다" else "새 댓글이 달렸습니다",
            body = if (isSecret) "비밀 댓글이 달렸습니다" else truncate(content),
            url = "/community/posts/$postId",
        )

    /** 스크랩한 경매 품목의 오늘자 시세 업데이트(가나다순, 앞 2개 + 외 N건). */
    fun auctionScrapUpdate(pumNames: List<String>): PushContent {
        val sorted = pumNames.sorted()
        val body =
            when {
                sorted.size == 1 -> "스크랩한 ${sorted[0]}의 경매 시세가 업데이트되었습니다"
                sorted.size == 2 -> "스크랩한 ${sorted[0]}·${sorted[1]}의 경매 시세가 업데이트되었습니다"
                else -> "스크랩한 ${sorted[0]}·${sorted[1]} 외 ${sorted.size - 2}건의 경매 시세가 업데이트되었습니다"
            }
        return PushContent(title = "스크랩한 경매 시세 업데이트", body = body, url = "/admin/insights")
    }

    /** 오늘 새로 추가된 지원사업 N건. */
    fun grantNew(
        count: Int,
        firstTitle: String?,
    ): PushContent {
        val body =
            if (count == 1 && firstTitle != null) {
                "새로운 지원사업이 추가되었습니다: $firstTitle"
            } else {
                "새로운 지원사업 ${count}건이 추가되었습니다"
            }
        return PushContent(title = "새 지원사업", body = body, url = "/admin/insights")
    }

    /** 스크랩한 지원사업 마감 임박(D-1) / 당일(D-day). */
    fun grantDeadline(
        title: String,
        daysLeft: Long,
    ): PushContent =
        if (daysLeft <= 0) {
            PushContent(title = "지원사업 오늘 마감", body = "$title 마감이 오늘입니다", url = "/admin/insights")
        } else {
            PushContent(title = "지원사업 마감 임박", body = "$title 마감이 ${daysLeft}일 남았습니다", url = "/admin/insights")
        }

    private fun truncate(text: String): String = if (text.length > BODY_MAX) text.take(BODY_MAX) + "…" else text

    private fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    private const val BODY_MAX = 50
}
