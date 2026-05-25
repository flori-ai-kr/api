package kr.ai.flori.subscriptions.gating

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ai.flori.subscriptions.service.SubscriptionService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * @RequiresSubscription 이 붙은 핸들러 진입 전 활성 구독을 강제한다.
 * 인증 필터(TenantContext set) 이후에 실행되므로 현재 사용자 기준으로 검사한다.
 * 미구독 시 [SubscriptionService.requireActiveSubscription]가 던지는 403이 @ControllerAdvice로 매핑된다.
 *
 * SubscriptionService를 ObjectProvider로 지연 주입 → @WebMvcTest 같은 슬라이스(서비스 빈 부재)에서도
 * 인터셉터가 구성되고, 게이팅 대상 핸들러에 진입할 때만 서비스를 실제 해석한다.
 */
@Component
class SubscriptionAccessInterceptor(
    private val subscriptionService: ObjectProvider<SubscriptionService>,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler is HandlerMethod && requiresSubscription(handler)) {
            subscriptionService.getObject().requireActiveSubscription()
        }
        return true
    }

    private fun requiresSubscription(handler: HandlerMethod): Boolean =
        handler.hasMethodAnnotation(RequiresSubscription::class.java) ||
            handler.beanType.isAnnotationPresent(RequiresSubscription::class.java)
}
