package kr.ai.flori.common.push

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 딥링크 SSOT. 한 곳에서 web 어드민 경로와 모바일 시맨틱(type/id)을 함께 정의해
 * 템플릿마다 경로를 손으로 박아 드리프트되는 것을 막는다.
 */
class PushLinksTest {
    @Test
    fun `community - web 어드민 경로 + 모바일 type, id`() {
        val link = PushLinks.community(42)

        assertThat(link.webUrl).isEqualTo("/admin/community/42")
        assertThat(link.mobileType).isEqualTo("community")
        assertThat(link.id).isEqualTo("42")
        assertThat(link.toData()).isEqualTo(mapOf("type" to "community", "id" to "42"))
    }

    @Test
    fun `calendar - id 없는 화면 진입`() {
        val link = PushLinks.calendar()

        assertThat(link.webUrl).isEqualTo("/admin/calendar")
        assertThat(link.mobileType).isEqualTo("calendar")
        assertThat(link.id).isNull()
        assertThat(link.toData()).isEqualTo(mapOf("type" to "calendar"))
    }

    @Test
    fun `insights - 인사이트 탭`() {
        val link = PushLinks.insights()

        assertThat(link.webUrl).isEqualTo("/admin/insights")
        assertThat(link.mobileType).isEqualTo("insight")
        assertThat(link.toData()).isEqualTo(mapOf("type" to "insight"))
    }

    @Test
    fun `support - 문의 상세`() {
        val link = PushLinks.support(7)

        assertThat(link.webUrl).isEqualTo("/admin/support/7")
        assertThat(link.mobileType).isEqualTo("support")
        assertThat(link.id).isEqualTo("7")
    }

    @Test
    fun `home - 대시보드`() {
        val link = PushLinks.home()

        assertThat(link.webUrl).isEqualTo("/")
        assertThat(link.mobileType).isEqualTo("dashboard")
    }

    @Test
    fun `toData - mobileType 없으면 빈 맵 (브로드캐스트 free-form)`() {
        val link = PushLink(webUrl = "/admin/insights")

        assertThat(link.toData()).isEmpty()
    }
}
