package kr.ai.flori.billing.config

import kr.ai.flori.billing.ratelimit.CouponRedeemRateLimitInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CouponWebConfig(
    private val interceptor: CouponRedeemRateLimitInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns("/coupons/redeem")
    }
}
