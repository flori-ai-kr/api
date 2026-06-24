package kr.ai.flori.billing.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** 빌링 모듈 설정. TossPaymentsProperties 바인딩 활성화. */
@Configuration
@EnableConfigurationProperties(TossPaymentsProperties::class)
class BillingConfig
