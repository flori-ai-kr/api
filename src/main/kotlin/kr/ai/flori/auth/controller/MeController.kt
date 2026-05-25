package kr.ai.flori.auth.controller

import kr.ai.flori.auth.dto.UserResponse
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 현재 로그인 사용자 조회. 보호 엔드포인트(인증 필요) — JWT 필터 + TenantContext 격리를 활용.
 */
@RestController
class MeController(
    private val userRepository: UserRepository,
) {
    @GetMapping("/me")
    fun me(): UserResponse {
        val userId = TenantContext.currentUserId()
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(ErrorCode.UNAUTHORIZED) }
        return UserResponse(id = userId, email = user.email, name = user.name)
    }
}
