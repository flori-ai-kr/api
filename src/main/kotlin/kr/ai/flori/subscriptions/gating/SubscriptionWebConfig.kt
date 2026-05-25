package kr.ai.flori.subscriptions.gating

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 구독 게이팅 인터셉터 등록. 도메인 자체 설정으로 캡슐화(다른 도메인 설정 미수정). */
@Configuration
class SubscriptionWebConfig(
    private val subscriptionAccessInterceptor: SubscriptionAccessInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(subscriptionAccessInterceptor)
    }
}
