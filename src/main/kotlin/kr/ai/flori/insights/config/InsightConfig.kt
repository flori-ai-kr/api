package kr.ai.flori.insights.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** 인사이트(정보 피드) 설정 등록 — aT 화훼유통정보 f001 적재. */
@Configuration
@EnableConfigurationProperties(FlowerApiProperties::class)
class InsightConfig
