package kr.ai.flori.billing.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 토스페이먼츠 자동결제 설정. secretKey는 환경변수만(코드/깃 금지). */
@ConfigurationProperties(prefix = "toss")
data class TossPaymentsProperties(
    val secretKey: String,
    val baseUrl: String = "https://api.tosspayments.com",
    val connectTimeoutMs: Int = 3000,
    val readTimeoutMs: Int = 10000,
)
