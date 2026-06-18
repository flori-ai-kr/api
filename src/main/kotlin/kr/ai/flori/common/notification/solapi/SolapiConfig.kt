package kr.ai.flori.common.notification.solapi

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(SolapiProperties::class)
class SolapiConfig
