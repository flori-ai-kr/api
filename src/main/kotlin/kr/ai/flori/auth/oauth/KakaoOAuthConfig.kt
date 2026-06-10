package kr.ai.flori.auth.oauth

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** 소셜 OAuth 자격증명 바인딩 등록(JwtProperties 등 기존 방식과 동일). 카카오·구글·네이버 공통. */
@Configuration
@EnableConfigurationProperties(
    KakaoProperties::class,
    GoogleProperties::class,
    NaverProperties::class,
)
class KakaoOAuthConfig
