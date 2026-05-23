package com.hazel.auth.controller

import com.hazel.auth.dto.UserResponse
import com.hazel.auth.repository.UserRepository
import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 현재 로그인 사용자 조회. 보호 엔드포인트(인증 필요) — JWT 필터 + TenantContext 격리를 활용.
 */
@Tag(name = "Me", description = "현재 사용자")
@RestController
class MeController(
    private val userRepository: UserRepository,
) {
    @Operation(summary = "내 정보", description = "토큰의 사용자 식별로 본인 정보 반환")
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
