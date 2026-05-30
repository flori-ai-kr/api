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
        // 운영 콘솔 경로에만 적용. 실제 가드는 @RequiresAdmin 어노테이션 유무로 동작하지만,
        // 경로를 명시해 admin 외 핸들러에서 불필요하게 인터셉터가 도는 것을 방지한다.
        registry.addInterceptor(adminInterceptor).addPathPatterns("/admin/**")
    }
}
