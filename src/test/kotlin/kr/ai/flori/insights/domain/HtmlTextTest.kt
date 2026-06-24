package kr.ai.flori.insights.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 공고 본문 HTML 정제기 검증. 기업마당 bsnsSumryCn 등에 <p>·<br>·style·HTML 엔티티가 섞여 와서
 * 화면에 raw 로 노출되는 문제를 막는다.
 */
class HtmlTextTest {
    @Test
    fun `태그를 제거하고 공백으로 분리한다`() {
        assertThat(HtmlText.clean("<p>안녕</p><br>하세요")).isEqualTo("안녕 하세요")
        assertThat(HtmlText.clean("""<p style="line-height: 1.8;">☞ 내용</p>""")).isEqualTo("☞ 내용")
    }

    @Test
    fun `HTML 엔티티를 복원한다`() {
        assertThat(HtmlText.clean("&quot;회복&quot;세 &amp;&nbsp;전망")).isEqualTo("\"회복\"세 & 전망")
    }

    @Test
    fun `연속 공백·줄바꿈을 한 칸으로 줄인다`() {
        assertThat(HtmlText.clean("줄1\r\n줄2  \t 줄3")).isEqualTo("줄1 줄2 줄3")
    }

    @Test
    fun `정상 텍스트는 그대로 둔다`() {
        assertThat(HtmlText.clean("정책자금 융자 지원")).isEqualTo("정책자금 융자 지원")
    }

    @Test
    fun `널·빈값·태그만 있으면 null`() {
        assertThat(HtmlText.clean(null)).isNull()
        assertThat(HtmlText.clean("   <p></p>  ")).isNull()
    }
}
