package kr.ai.flori.insights.domain

/**
 * 외부 API가 준 URL을 클라이언트 노출용으로 적재하기 전 스킴 검증.
 * http/https 만 허용 — javascript:·data:·file:·//protocol-relative 등은 null(미적재).
 * 공공 API 응답을 신뢰하지 않고 web 의 href 렌더 XSS 방어선을 서버 단에 둔다.
 */
object Urls {
    fun httpOrNull(url: String?): String? =
        url
            ?.trim()
            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
}
