package kr.ai.flori.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/** 네이버 OAuth 자격증명. client-secret은 서버에만 보관(앱에 노출 금지). */
@ConfigurationProperties(prefix = "naver")
data class NaverProperties(
    val clientId: String = "",
    val clientSecret: String = "",
)
