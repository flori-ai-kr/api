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
        assertThat(content.link).isEqualTo(PushLinks.calendar())
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
        assertThat(content.link).isEqualTo(PushLinks.calendar())
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
        assertThat(content.link).isEqualTo(PushLinks.home())
    }

    // ── 신규 5종 ──────────────────────────────────────────────────────────────

    @Test
    fun `communityNotice`() {
        val content = PushTemplates.communityNotice(42, "6월 정기 점검 안내")

        assertThat(content.title).isEqualTo("새 공지사항")
        assertThat(content.body).isEqualTo("6월 정기 점검 안내")
        assertThat(content.link).isEqualTo(PushLinks.community(42))
    }

    @Test
    fun `communityComment - 댓글`() {
        val content = PushTemplates.communityComment(7, "좋은 정보 감사합니다", isReply = false, isSecret = false)

        assertThat(content.title).isEqualTo("새 댓글이 달렸습니다")
        assertThat(content.body).isEqualTo("좋은 정보 감사합니다")
        assertThat(content.link).isEqualTo(PushLinks.community(7))
    }

    @Test
    fun `communityComment - 답글`() {
        val content = PushTemplates.communityComment(7, "저도요", isReply = true, isSecret = false)

        assertThat(content.title).isEqualTo("새 답글이 달렸습니다")
    }

    @Test
    fun `communityComment - 비밀이면 본문 마스킹`() {
        val content = PushTemplates.communityComment(7, "민감한 내용", isReply = false, isSecret = true)

        assertThat(content.body).isEqualTo("비밀 댓글이 달렸습니다")
    }

    @Test
    fun `communityComment - 50자 초과 시 말줄임`() {
        val long = "가".repeat(60)
        val content = PushTemplates.communityComment(7, long, isReply = false, isSecret = false)

        assertThat(content.body).hasSize(51) // 50자 + …
        assertThat(content.body).endsWith("…")
    }

    @Test
    fun `auctionScrapUpdate - 가나다순 앞2개 외 N건`() {
        val content = PushTemplates.auctionScrapUpdate(listOf("장미", "국화", "프리지아", "거베라"))

        assertThat(content.title).isEqualTo("스크랩한 경매 시세 업데이트")
        // 가나다: 거베라, 국화, 장미, 프리지아 → 앞 2개 + 외 2건
        assertThat(content.body).isEqualTo("스크랩한 거베라·국화 외 2건의 경매 시세가 업데이트되었습니다")
        assertThat(content.link).isEqualTo(PushLinks.insights())
    }

    @Test
    fun `auctionScrapUpdate - 1개`() {
        val content = PushTemplates.auctionScrapUpdate(listOf("국화"))
        assertThat(content.body).isEqualTo("스크랩한 국화의 경매 시세가 업데이트되었습니다")
    }

    @Test
    fun `auctionScrapUpdate - 2개`() {
        val content = PushTemplates.auctionScrapUpdate(listOf("장미", "국화"))
        assertThat(content.body).isEqualTo("스크랩한 국화·장미의 경매 시세가 업데이트되었습니다")
    }

    @Test
    fun `grantNew - 1건은 제목 포함`() {
        val content = PushTemplates.grantNew(1, "소상공인 온라인판로 지원사업")
        assertThat(content.title).isEqualTo("새 지원사업")
        assertThat(content.body).isEqualTo("새로운 지원사업이 추가되었습니다: 소상공인 온라인판로 지원사업")
    }

    @Test
    fun `grantNew - N건`() {
        val content = PushTemplates.grantNew(3, "아무 제목")
        assertThat(content.body).isEqualTo("새로운 지원사업 3건이 추가되었습니다")
    }

    @Test
    fun `grantDeadline - D-1`() {
        val content = PushTemplates.grantDeadline("소상공인 융자", 1)
        assertThat(content.title).isEqualTo("지원사업 마감 임박")
        assertThat(content.body).isEqualTo("소상공인 융자 마감이 1일 남았습니다")
    }

    @Test
    fun `grantDeadline - D-day`() {
        val content = PushTemplates.grantDeadline("소상공인 융자", 0)
        assertThat(content.title).isEqualTo("지원사업 오늘 마감")
        assertThat(content.body).isEqualTo("소상공인 융자 마감이 오늘입니다")
    }
}
