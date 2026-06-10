package kr.ai.flori.common.notification.discord

/**
 * Discord 논리 알림 채널. 각 채널이 어떤 웹훅 URL로 가는지만 안다.
 * 새 알림 종류 추가 시: 여기에 항목 + DiscordProperties에 URL 필드 추가.
 */
enum class DiscordChannel(
    val urlSelector: (DiscordProperties) -> String,
) {
    SIGNUP({ it.signupWebhookUrl }),
    VERIFICATION({ it.verificationWebhookUrl }),
    WAITLIST({ it.waitlistWebhookUrl }),
    INTERVIEW({ it.interviewWebhookUrl }),
}
