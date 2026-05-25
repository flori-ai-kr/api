package kr.ai.flori.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * @Scheduled 활성화 (고정비 자동생성 등 Vercel Cron 대체).
 */
@Configuration
@EnableScheduling
class ScheduleConfig
