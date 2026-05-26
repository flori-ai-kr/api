package kr.ai.flori.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/** 카카오 OAuth 자격증명. client_secret은 서버에만 보관(앱에 노출 금지). */
@ConfigurationProperties(prefix = "kakao")
data class KakaoProperties(
    val restApiKey: String = "",
    val clientSecret: String = "",
)
