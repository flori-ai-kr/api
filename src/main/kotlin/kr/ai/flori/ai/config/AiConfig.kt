package kr.ai.flori.ai.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** AI 게이트웨이 설정 등록. */
@Configuration
@EnableConfigurationProperties(AiGatewayProperties::class)
class AiConfig
