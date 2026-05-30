package kr.ai.flori.admin.gating

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.repository.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * @RequiresAdmin 핸들러 진입 전 현재 사용자의 is_admin 을 강제한다.
 * JWT 인증 필터(TenantContext set) 이후 실행 — 미인증이면 Security가 먼저 401로 막는다.
 * 인증됐으나 운영자가 아니면 403. ObjectProvider 지연 주입으로 슬라이스 테스트에서도 구성된다.
 */
@Component
class AdminInterceptor(
    private val userRepository: ObjectProvider<UserRepository>,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler is HandlerMethod && requiresAdmin(handler)) {
            val userId = TenantContext.currentUserId()
            val user =
                userRepository.getObject().findById(userId).orElse(null)
                    ?: throw AppException(AdminErrorCode.FORBIDDEN_NOT_ADMIN)
            if (!user.isAdmin) throw AppException(AdminErrorCode.FORBIDDEN_NOT_ADMIN)
        }
        return true
    }

    private fun requiresAdmin(handler: HandlerMethod): Boolean =
        handler.hasMethodAnnotation(RequiresAdmin::class.java) ||
            handler.beanType.isAnnotationPresent(RequiresAdmin::class.java)
}
