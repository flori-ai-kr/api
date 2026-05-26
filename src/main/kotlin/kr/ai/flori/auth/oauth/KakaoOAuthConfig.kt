package kr.ai.flori.auth.oauth

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** 카카오 OAuth 설정 바인딩 등록(JwtProperties 등 기존 방식과 동일). */
@Configuration
@EnableConfigurationProperties(KakaoProperties::class)
class KakaoOAuthConfig
