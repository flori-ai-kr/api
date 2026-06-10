package kr.ai.flori.common.notification.discord

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Discord 알림 채널별 웹훅 URL. 실제 값은 환경변수(${ENV})에서만 해결(시크릿 금지).
 * 운영 에러 알림(discord.webhook-url)은 DiscordErrorReporter가 @Value로 별도 관리한다.
 */
@ConfigurationProperties(prefix = "discord")
data class DiscordProperties(
    /** 유저 가입 알림 채널 (DISCORD_SIGNUP_WEBHOOK_URL). */
    val signupWebhookUrl: String = "",
    /** 사업자 인증 신청 알림 채널 (DISCORD_VERIFICATION_WEBHOOK_URL). */
    val verificationWebhookUrl: String = "",
    /** 사전등록 알림 채널 (DISCORD_WAITLIST_WEBHOOK_URL). */
    val waitlistWebhookUrl: String = "",
    /** 유저 인터뷰 신청 알림 채널 (DISCORD_INTERVIEW_WEBHOOK_URL). */
    val interviewWebhookUrl: String = "",
)
