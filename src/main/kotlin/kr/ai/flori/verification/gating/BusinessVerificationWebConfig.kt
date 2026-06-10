package kr.ai.flori.verification.gating

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 사업자 인증 게이팅 인터셉터 등록(도메인 자체 설정으로 캡슐화). */
@Configuration
class BusinessVerificationWebConfig(
    private val businessVerifiedInterceptor: BusinessVerifiedInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(businessVerifiedInterceptor)
    }
}
