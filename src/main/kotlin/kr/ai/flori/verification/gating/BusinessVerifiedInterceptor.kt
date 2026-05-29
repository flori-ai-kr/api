package kr.ai.flori.verification.gating

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ai.flori.verification.service.BusinessVerificationService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * @RequiresBusinessVerified 핸들러 진입 전 APPROVED 인증을 강제한다.
 * 인증 필터(TenantContext set) 이후 실행. 미인증 시 서비스가 던지는 403이 @ControllerAdvice로 매핑된다.
 * ObjectProvider 지연 주입 → 서비스 빈 부재 슬라이스 테스트에서도 인터셉터가 구성된다.
 */
@Component
class BusinessVerifiedInterceptor(
    private val service: ObjectProvider<BusinessVerificationService>,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler is HandlerMethod && requiresVerified(handler)) {
            service.getObject().requireVerified()
        }
        return true
    }

    private fun requiresVerified(handler: HandlerMethod): Boolean =
        handler.hasMethodAnnotation(RequiresBusinessVerified::class.java) ||
            handler.beanType.isAnnotationPresent(RequiresBusinessVerified::class.java)
}
