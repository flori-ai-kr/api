package kr.ai.flori.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/** 구글 OAuth 자격증명. client-secret은 서버에만 보관(앱에 노출 금지). */
@ConfigurationProperties(prefix = "google")
data class GoogleProperties(
    val clientId: String = "",
    val clientSecret: String = "",
)
