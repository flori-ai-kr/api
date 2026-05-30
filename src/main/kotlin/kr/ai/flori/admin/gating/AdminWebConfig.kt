package kr.ai.flori.admin.gating

import kr.ai.flori.admin.config.AiHealthProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 운영자 게이팅 인터셉터 등록 + admin 도메인 설정 프로퍼티 바인딩(도메인 자체 설정으로 캡슐화).
 */
@Configuration
@EnableConfigurationProperties(AiHealthProperties::class)
class AdminWebConfig(
    private val adminInterceptor: AdminInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminInterceptor)
    }
}
