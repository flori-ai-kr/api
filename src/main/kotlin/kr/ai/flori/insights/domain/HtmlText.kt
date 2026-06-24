package kr.ai.flori.insights.domain

/**
 * 공고 본문 HTML 정제기. 기업마당 bsnsSumryCn 등 응답에 <p>·<br>·style 속성·HTML 엔티티가 섞여 와
 * 화면에 raw 로 노출되는 것을 막는다. 태그는 공백으로 치환(단어 병합 방지) 후 연속 공백을 한 칸으로 줄인다.
 */
object HtmlText {
    private val TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("\\s+")

    /** 태그 제거 + 흔한 엔티티 복원 + 공백 정규화. 정제 후 빈 문자열이면 null. &amp; 는 마지막에 풀어 이중 복원 방지. */
    fun clean(raw: String?): String? {
        val s = raw ?: return null
        val cleaned =
            s
                .replace(TAG, " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace(WHITESPACE, " ")
                .trim()
        return cleaned.ifBlank { null }
    }
}
