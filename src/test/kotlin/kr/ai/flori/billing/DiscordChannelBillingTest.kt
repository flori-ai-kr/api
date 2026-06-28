package kr.ai.flori.billing

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscordChannelBillingTest {
    @Test
    fun `BILLING 채널은 billingWebhookUrl 을 선택한다`() {
        val props = DiscordProperties(billingWebhookUrl = "https://discord/billing")
        assertThat(DiscordChannel.BILLING.urlSelector(props)).isEqualTo("https://discord/billing")
    }
}
