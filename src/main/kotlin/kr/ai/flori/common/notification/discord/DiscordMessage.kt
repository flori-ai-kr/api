package kr.ai.flori.common.notification.discord

/**
 * Discord 웹훅 페이로드(단순 텍스트). content는 마크다운 지원.
 */
data class DiscordMessage(
    val content: String,
) {
    companion object {
        fun of(content: String): DiscordMessage = DiscordMessage(content)
    }
}
