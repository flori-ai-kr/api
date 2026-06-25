package kr.ai.flori.common.push

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalTime

class PushTemplatesTest {
    @Test
    fun `pickupReminder - 시간, 금액 모두 있을 때`() {
        val content =
            PushTemplates.pickupReminder(
                time = LocalTime.of(14, 0),
                customerName = "박지현",
                title = "졸업식 꽃다발",
                amount = 180000,
            )

        assertThat(content.title).isEqualTo("예약 리마인더")
        assertThat(content.body).isEqualTo("14:00 · 박지현님 · 졸업식 꽃다발 · 180,000원")
        assertThat(content.url).isEqualTo("/calendar")
    }

    @Test
    fun `pickupReminder - 시간 없을 때`() {
        val content =
            PushTemplates.pickupReminder(
                time = null,
                customerName = "김영희",
                title = "개업 화환",
                amount = 50000,
            )

        assertThat(content.body).isEqualTo("김영희님 · 개업 화환 · 50,000원")
    }

    @Test
    fun `pickupReminder - 금액 0일 때 생략`() {
        val content =
            PushTemplates.pickupReminder(
                time = LocalTime.of(9, 30),
                customerName = "이철수",
                title = "프로포즈 부케",
                amount = 0,
            )

        assertThat(content.body).isEqualTo("09:30 · 이철수님 · 프로포즈 부케")
    }

    @Test
    fun `dailySummary - 3건 이하`() {
        val items =
            listOf(
                DailySummaryItem(LocalTime.of(14, 0), "박지현", "졸업식 꽃다발"),
                DailySummaryItem(LocalTime.of(9, 30), "홍길동", "프로포즈 부케"),
            )

        val content = PushTemplates.dailySummary(items)

        assertThat(content.title).isEqualTo("오늘 예약 2건")
        assertThat(content.body).isEqualTo("09:30 홍길동님 · 프로포즈 부케\n14:00 박지현님 · 졸업식 꽃다발")
        assertThat(content.url).isEqualTo("/calendar")
    }

    @Test
    fun `dailySummary - 3건 초과 시 외 N건 표시`() {
        val items =
            listOf(
                DailySummaryItem(LocalTime.of(9, 0), "A", "상품1"),
                DailySummaryItem(LocalTime.of(10, 0), "B", "상품2"),
                DailySummaryItem(LocalTime.of(11, 0), "C", "상품3"),
                DailySummaryItem(LocalTime.of(14, 0), "D", "상품4"),
                DailySummaryItem(LocalTime.of(16, 0), "E", "상품5"),
            )

        val content = PushTemplates.dailySummary(items)

        assertThat(content.title).isEqualTo("오늘 예약 5건")
        assertThat(content.body).contains("09:00 A님 · 상품1")
        assertThat(content.body).contains("10:00 B님 · 상품2")
        assertThat(content.body).contains("11:00 C님 · 상품3")
        assertThat(content.body).contains("외 2건")
        assertThat(content.body).doesNotContain("D님")
    }

    @Test
    fun `dailySummary - 시간 없는 항목`() {
        val items =
            listOf(
                DailySummaryItem(null, "홍길동", "꽃다발"),
            )

        val content = PushTemplates.dailySummary(items)

        assertThat(content.body).isEqualTo("홍길동님 · 꽃다발")
    }

    @Test
    fun `testNotification`() {
        val content = PushTemplates.testNotification()

        assertThat(content.title).isEqualTo("테스트 알림")
        assertThat(content.body).isEqualTo("푸시 알림이 정상적으로 동작합니다")
    }
}
