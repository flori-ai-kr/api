package kr.ai.flori.common.notification.discord

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Discord 알림 모듈 설정. DiscordProperties 바인딩 활성화. */
@Configuration
@EnableConfigurationProperties(DiscordProperties::class)
class DiscordConfig
