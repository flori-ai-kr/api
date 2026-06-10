package kr.ai.flori.common.notification.discord

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class DiscordNotifierTest {
    @Test
    fun `웹훅 URL 미설정이면 전송하지 않고 예외도 던지지 않는다`() {
        val notifier = DiscordNotifier(DiscordProperties(signupWebhookUrl = "", verificationWebhookUrl = ""))

        assertDoesNotThrow {
            notifier.notify(DiscordChannel.SIGNUP, DiscordMessage.of("테스트"))
            notifier.notify(DiscordChannel.VERIFICATION, DiscordMessage.of("테스트"))
        }
    }
}
